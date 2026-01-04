import os
import re
from typing import List, Dict, Any

from biz.utils.log import logger


class ContextDetector:
    """使用LLM智能判断是否需要获取代码上下文"""
    
    CONTEXT_DETECTION_PROMPT = """你是代码安全分析专家。判断以下Java代码改动是否需要获取完整代码上下文来进行安全评审。

需要上下文的场景：
- 权限控制相关（@PreAuthorize、@PostAuthorize、@Secured、授权、鉴权）
- 数据库操作（SQL、JPA、MyBatis，可能存在SQL注入、越权访问）
- 文件操作（File、Path、FileInputStream等，可能存在路径遍历）
- 敏感数据处理（用户信息、订单、支付、账户等业务）
- Controller层的增删改操作（@PostMapping、@PutMapping、@DeleteMapping）
- Service层调用了其他服务方法（需要理解完整业务流程）
- 涉及敏感配置的修改

不需要上下文的场景：
- 纯配置文件修改（.properties、.yml）
- 注释、文档修改
- 日志输出语句
- 简单的getter/setter方法
- 纯工具类方法（字符串处理、日期格式化等）
- 单元测试代码

代码改动摘要：
{diff_summary}

请直接回答：需要 或 不需要
只输出这两个词之一，不要有其他内容。"""
    
    def __init__(self, llm_client):
        """
        初始化上下文检测器
        
        Args:
            llm_client: LLM客户端实例
        """
        self.client = llm_client
        self.detection_timeout = int(os.getenv('CONTEXT_DETECTION_TIMEOUT', '10'))
    
    def needs_context(self, changes_text: str, changed_files: str) -> bool:
        """
        判断是否需要获取代码上下文
        
        Args:
            changes_text: diff代码文本
            changed_files: 修改的文件列表（逗号分隔）
        
        Returns:
            True表示需要上下文，False表示不需要
        """
        try:
            # 提取diff摘要
            diff_summary = self._extract_diff_summary(changes_text, changed_files)
            
            # 如果摘要太短，可能是简单修改，不需要上下文
            if len(diff_summary) < 50:
                logger.info("代码改动摘要过短，判断为不需要上下文")
                return False
            
            # 调用LLM判断
            prompt = self.CONTEXT_DETECTION_PROMPT.format(diff_summary=diff_summary)
            
            messages = [
                {"role": "system", "content": "你是代码安全分析专家，擅长快速判断代码改动的风险等级。"},
                {"role": "user", "content": prompt}
            ]
            
            logger.info("调用LLM判断是否需要上下文...")
            response = self.client.completions(messages=messages)
            
            # 解析响应
            needs = self._parse_response(response)
            
            logger.info(f"LLM判断结果: {'需要' if needs else '不需要'}上下文")
            return needs
            
        except Exception as e:
            logger.error(f"LLM判断是否需要上下文失败: {e}，默认返回不需要")
            # 出错时降级为不需要上下文，避免影响正常评审流程
            return False
    
    def _extract_diff_summary(self, changes_text: str, changed_files: str) -> str:
        """
        提取diff代码的关键信息摘要（简化版）
        
        Args:
            changes_text: diff代码文本
            changed_files: 修改的文件列表
        
        Returns:
            摘要文本
        """
        try:
            summary_parts = []
            
            # 1. 添加修改的文件列表
            summary_parts.append(f"修改的文件: {changed_files}")
            
            # 2. 快速提取关键信息
            annotations = self._extract_annotations(changes_text)
            if annotations:
                summary_parts.append(f"涉及的注解: {', '.join(annotations[:10])}")
            
            keywords = self._extract_security_keywords(changes_text)
            if keywords:
                summary_parts.append(f"安全相关关键词: {', '.join(keywords[:10])}")
            
            # 3. 截取部分diff内容（前1500字符）
            truncated_diff = changes_text[:1500] if len(changes_text) > 1500 else changes_text
            summary_parts.append(f"\n代码片段:\n{truncated_diff}")
            
            return "\n".join(summary_parts)
            
        except Exception as e:
            logger.error(f"提取diff摘要失败: {e}")
            # 降级为返回原始diff的前2000字符
            return changes_text[:2000]
    
    def _extract_annotations(self, text: str) -> List[str]:
        """提取注解"""
        # 匹配: @AnnotationName
        pattern = r'@([A-Z][a-zA-Z0-9_]*)'
        matches = re.findall(pattern, text)
        return list(set(matches))  # 去重
    
    def _extract_security_keywords(self, text: str) -> List[str]:
        """提取安全相关关键词"""
        security_keywords = [
            'authorize', 'authentication', 'permission', 'role', 'login', 'logout',
            'password', 'token', 'session', 'security', 'credential',
            'sql', 'query', 'execute', 'database', 'jdbc',
            'file', 'path', 'stream', 'read', 'write',
            'user', 'order', 'payment', 'account', 'money',
            '权限', '认证', '授权', '鉴权', '登录', '密码', '令牌'
        ]
        
        found_keywords = []
        text_lower = text.lower()
        for keyword in security_keywords:
            if keyword in text_lower:
                found_keywords.append(keyword)
        
        return list(set(found_keywords))  # 去重
    
    def _parse_response(self, response: str) -> bool:
        """
        解析LLM响应
        
        Args:
            response: LLM返回的文本
        
        Returns:
            True表示需要上下文，False表示不需要
        """
        # 清理响应文本
        response = response.strip().lower()
        
        # 判断是否包含"需要"
        if '需要' in response and '不需要' not in response:
            return True
        elif '不需要' in response:
            return False
        
        # 英文判断
        if 'need' in response and 'not' not in response and "don't" not in response:
            return True
        elif 'not need' in response or "don't need" in response:
            return False
        
        # 默认不需要（保守策略）
        logger.warning(f"无法解析LLM响应: {response}, 默认返回不需要")
        return False

