import abc
import os
import re
from typing import Dict, Any, List

from biz.llm.factory import Factory
from biz.utils.log import logger
from biz.utils.token_util import count_tokens, truncate_text_by_tokens


class BaseReviewer(abc.ABC):
    """代码审查基类"""

    def __init__(self, prompt_key: str = None):
        self.client = Factory().getClient()
        self.prompts = self._load_prompts()

    def _load_prompts(self) -> Dict[str, Any]:
        """加载提示词配置（从 markdown 文件）"""
        prompt_file = "conf/review_prompt_eval.md"
        try:
            with open(prompt_file, "r", encoding="utf-8") as file:
                prompt_content = file.read()
                
                # Markdown 格式的提示词直接作为 system prompt
                return {
                    "system_message": {"role": "system", "content": prompt_content},
                    "user_message_template": "以下是某位员工向 GitLab 代码库提交的代码，请以professional风格审查以下代码。\n\n代码变更内容：\n{diff}\n\n提交历史(commits)：\n{commits}\n\n{context}"
                }
        except FileNotFoundError as e:
            logger.error(f"加载提示词配置失败: {e}")
            raise Exception(f"提示词配置加载失败: {e}")

    def call_llm(self, messages: List[Dict[str, Any]]) -> str:
        """调用 LLM 进行代码审核"""
        logger.info(f"向 AI 发送代码 Review 请求, messages: {messages}")
        review_result = self.client.completions(messages=messages)
        logger.info(f"收到 AI 返回结果: {review_result}")
        return review_result

    @abc.abstractmethod
    def review_code(self, *args, **kwargs) -> str:
        """抽象方法，子类必须实现"""
        pass


class CodeReviewer(BaseReviewer):
    """代码 Diff 级别的审查"""

    def __init__(self):
        super().__init__("code_review_prompt")
        self.context_enabled = os.getenv("CONTEXT_ENABLED", "0") == "1"

    def review_and_strip_code(self, changes_text: str, commits_text: str = "", changed_files: str = "",
                             repo_url: str = None, source_branch: str = None, project_name: str = None,
                             access_token: str = None) -> str:
        """
        Review判断changes_text超出取前REVIEW_MAX_TOKENS个token，超出则截断changes_text，
        调用review_code方法，返回review_result，如果review_result是markdown格式，则去掉头尾的```
        :param changes_text: diff代码
        :param commits_text: 提交信息
        :param changed_files: 修改的文件列表
        :param repo_url: 仓库URL（用于克隆获取上下文）
        :param source_branch: 源分支名（Merge Request场景下为source_branch，Push场景下为当前分支）
        :param project_name: 项目名称
        :param access_token: 访问令牌
        :return: 评审结果
        """
        # 如果超长，取前REVIEW_MAX_TOKENS个token
        review_max_tokens = int(os.getenv("REVIEW_MAX_TOKENS", 10000))
        # 如果changes为空,打印日志
        if not changes_text:
            logger.info("代码为空, diffs_text = %", str(changes_text))
            return "代码为空"

        # 计算tokens数量，如果超过REVIEW_MAX_TOKENS，截断changes_text
        tokens_count = count_tokens(changes_text)
        if tokens_count > review_max_tokens:
            changes_text = truncate_text_by_tokens(changes_text, review_max_tokens)

        # 获取代码上下文（如果启用）
        context_section = ""
        if self.context_enabled and repo_url and self._is_java_code(changed_files):
            try:
                # 步骤1: 使用LLM判断是否需要上下文
                if self._llm_needs_context(changes_text, changed_files):
                    logger.info("LLM判断需要获取代码上下文")
                    
                    # 步骤2: 确保本地有仓库
                    from biz.utils.git_manager import GitManager
                    git_mgr = GitManager()
                    repo_path = git_mgr.ensure_repo(repo_url, source_branch, project_name, access_token)
                    logger.info(f"仓库路径: {repo_path}")
                    
                    # 步骤3: 使用JavaParser分析上下文
                    from biz.utils.java_context_analyzer import JavaContextAnalyzer
                    analyzer = JavaContextAnalyzer()
                    changed_file_list = [f.strip() for f in changed_files.split(',') if f.strip()]
                    # 直接传递diff文本作为参数，避免使用环境变量（解决并发问题）
                    context_section = analyzer.analyze(repo_path, changed_file_list, diff_text=changes_text)
                    
                    logger.info(f"成功获取代码上下文，长度: {len(context_section)}")
                else:
                    logger.info("LLM判断不需要获取代码上下文")
            except Exception as e:
                logger.error(f"获取代码上下文失败，降级为普通评审: {e}")
                import traceback
                logger.error(traceback.format_exc())
                context_section = ""
        elif self.context_enabled:
            if not repo_url:
                logger.info("未提供仓库URL，跳过上下文获取")
            elif not self._is_java_code(changed_files):
                logger.info("非Java代码，跳过上下文获取")

        review_result = self.review_code(changes_text, commits_text, changed_files, context_section).strip()
        if review_result.startswith("```markdown") and review_result.endswith("```"):
            return review_result[11:-3].strip()
        return review_result

    def _llm_needs_context(self, changes_text: str, changed_files: str) -> bool:
        """
        使用LLM判断是否需要上下文
        
        :param changes_text: diff代码文本
        :param changed_files: 修改的文件列表
        :return: True表示需要上下文，False表示不需要
        """
        try:
            from biz.utils.context_detector import ContextDetector
            detector = ContextDetector(self.client)
            return detector.needs_context(changes_text, changed_files)
        except Exception as e:
            logger.error(f"LLM判断是否需要上下文失败: {e}")
            return False
    
    def _is_java_code(self, changed_files: str) -> bool:
        """
        判断是否包含Java文件
        
        :param changed_files: 修改的文件列表
        :return: True表示包含Java文件
        """
        if not changed_files:
            return False
        return '.java' in changed_files.lower()

    def review_code(self, diffs_text: str, commits_text: str = "", changed_files: str = "", context_section: str = "") -> str:
        """
        Review 代码并返回结果
        
        :param diffs_text: diff代码
        :param commits_text: 提交信息
        :param changed_files: 修改的文件列表
        :param context_section: 代码上下文（可选）
        :return: 评审结果
        """
        # 系统提示词（包含评审规则和要求）
        system_content = self.prompts["system_message"]["content"]
        
        # 构建用户消息（代码diff + 提交信息 + 上下文）
        user_content = "以下是某位员工向 GitLab 代码库提交的代码，请以professional风格审查以下代码。\n\n"
        user_content += f"代码变更内容：\n{diffs_text}\n\n"
        user_content += f"提交历史(commits)：\n{commits_text}\n\n"
        
        if context_section:
            user_content += f"【代码上下文】（仅供参考，不要评审上下文代码）：\n{context_section}"
        
        messages = [
            {"role": "system", "content": system_content},
            {"role": "user", "content": user_content}
        ]
        return self.call_llm(messages)

    @staticmethod
    def parse_review_result(review_text: str) -> Dict[str, Any]:
        """
        解析评审结果，提取结构化数据
        :param review_text: LLM 返回的 JSON 或 文本
        :return: 包含 'score', 'issues' (问题列表) 的字典
        """
        if not review_text:
            return {"score": 0, "issues": []}

        result = {
            "score": 0,
            "issues": []
        }

        # 1. 尝试解析 JSON 块
        try:
            json_match = re.search(r'\{[\s\S]*?"问题列表"[\s\S]*?\[[\s\S]*?\][\s\S]*?\}', review_text)
            if json_match:
                json_data = json_match.group(0)
                # 修复可能的不规范 JSON (如尾部逗号) - 简单处理，如有必要可引入更强壮的 parser
                import json
                parsed_json = json.loads(json_data)
                
                # 提取分数
                score_val = parsed_json.get("总分", 0)
                if isinstance(score_val, (int, float)):
                    result["score"] = float(score_val)
                else:
                    # 尝试从字符串提取
                    score_match = re.search(r'(\d+(?:\.\d+)?)', str(score_val))
                    if score_match:
                        result["score"] = float(score_match.group(1))

                # 提取问题列表
                issues = parsed_json.get("问题列表", [])
                if isinstance(issues, list):
                    result["issues"] = issues
                
                return result
        except Exception as e:
            logger.warning(f"解析 Review JSON 失败: {e}")

        # 2. 如果 JSON 解析失败，尝试从文本提取分数
        if result["score"] == 0:
            score_match = re.search(r"总分[:：]\s*(\d+(?:\.\d+)?)分?", review_text)
            if score_match:
                result["score"] = float(score_match.group(1))
        
        return result

    @staticmethod
    def check_issue_match(text: str, target_issue: str) -> bool:
        """
        判断文本中是否包含目标问题（支持模糊匹配和关键词映射）
        :param text: 问题描述文本
        :param target_issue: 目标问题关键词 (如 "NPE", "SQL注入")
        :return: 是否匹配
        """
        text_lower = text.lower()
        target_lower = target_issue.lower()
        
        # 1. 精确子串匹配
        if target_lower in text_lower:
            return True
        
        # 2. 关键词同义词映射
        keyword_map = {
            "npe": ["空指针", "nullpointer", "npe", "null检查", "null判断"],
            "sql注入": ["sql注入", "sql injection", "字符串拼接", "preparedstatement", "参数化"],
            "xss": ["xss", "跨站脚本", "html转义", "输出过滤"],
            "水平越权": ["水平越权", "idor", "所有权", "资源访问", "用户id"],
            "垂直越权": ["垂直越权", "角色", "权限提升", "权限检查"],
            "资源泄漏": ["资源泄漏", "未关闭", "finally", "try-with-resources"],
            "死锁": ["死锁", "deadlock", "锁顺序"],
            "异常": ["异常", "exception", "try-catch", "throwable"],
            "性能": ["性能", "并发", "线程", "锁", "循环查询", "n+1"],
            "命名": ["命名", "变量名", "方法名", "规范"],
            "注释": ["注释", "javadoc"],
            "魔法值": ["魔法值", "magic number", "硬编码"],
            "日志": ["日志", "log", "堆栈"],
            "builder": ["builder", "构建者"],
            "lambda": ["lambda", "闭包"],
        }
        
        for key, synonyms in keyword_map.items():
            if key in target_lower:
                for synonym in synonyms:
                    if synonym in text_lower:
                        return True
        
        # 3. 组合词拆分匹配 (匹配 50% 以上的词)
        target_words = [w for w in re.split(r'[、，,\s]+', target_issue) if len(w) > 1]
        if len(target_words) >= 2:
            matched_words = sum(1 for word in target_words if word in text_lower)
            if matched_words / len(target_words) >= 0.5:
                return True
        
        return False

    @staticmethod
    def parse_review_score(review_text: str) -> int:
        """解析 AI 返回的 Review 结果，返回评分 (兼容旧接口)"""
        result = CodeReviewer.parse_review_result(review_text)
        return int(result.get("score", 0))

