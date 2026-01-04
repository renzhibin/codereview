import json
import os
import subprocess
from pathlib import Path
from typing import List, Dict, Any, Optional

from biz.utils.log import logger
from biz.utils.token_util import count_tokens


class JavaContextAnalyzer:
    """Java代码上下文分析器，使用JavaParser提取代码上下文"""
    
    def __init__(self):
        # java-tools项目路径（AI-Codereview-Gitlab/java-tools）
        current_dir = Path(__file__).parent.parent.parent
        self.java_tool_path = current_dir / "java-tools"
        self.max_context_tokens = int(os.getenv('CONTEXT_MAX_TOKENS', '20000'))
        self._ensure_java_tool_compiled()
    
    def _ensure_java_tool_compiled(self):
        """确保Java工具已编译"""
        try:
            target_dir = self.java_tool_path / "target"
            pom_file = self.java_tool_path / "pom.xml"
            
            if not pom_file.exists():
                logger.error(f"pom.xml不存在: {pom_file}")
                raise Exception("java-tools项目不存在")
            
            # 检查是否已编译
            if not target_dir.exists() or not (target_dir / "classes").exists():
                logger.info("JavaParser工具未编译，开始编译...")
                self._compile_java_tool()
            else:
                logger.info("JavaParser工具已编译")
        except Exception as e:
            logger.error(f"确保Java工具编译失败: {e}")
            raise
    
    def _compile_java_tool(self):
        """编译Java工具"""
        try:
            logger.info(f"编译JavaParser工具: {self.java_tool_path}")
            result = subprocess.run(
                ["mvn", "clean", "compile"],
                cwd=str(self.java_tool_path),
                capture_output=True,
                text=True,
                timeout=300
            )
            
            if result.returncode != 0:
                logger.error(f"Maven编译失败:\nSTDOUT: {result.stdout}\nSTDERR: {result.stderr}")
                raise Exception(f"Maven编译失败: {result.stderr}")
            
            logger.info("JavaParser工具编译成功")
        except subprocess.TimeoutExpired:
            logger.error("Maven编译超时（300秒）")
            raise Exception("Maven编译超时")
        except FileNotFoundError:
            logger.error("未找到mvn命令，请确保Maven已安装并在PATH中")
            raise Exception("未找到mvn命令")
    
    def analyze(self, repo_path: str, changed_files: List[str], diff_text: Optional[str] = None) -> str:
        """
        分析代码上下文
        
        Args:
            repo_path: 仓库本地路径
            changed_files: 修改的文件列表
            diff_text: diff代码文本（可选），用于精确识别被修改的方法
        
        Returns:
            格式化的上下文文本
        """
        try:
            logger.info(f"开始分析代码上下文，仓库路径: {repo_path}, 文件数: {len(changed_files)}")
            
            # 过滤出Java文件
            java_files = [f for f in changed_files if f.strip().endswith('.java')]
            if not java_files:
                logger.info("没有Java文件需要分析")
                return ""
            
            # 如果没有传入diff_text，尝试从环境变量获取（向后兼容）
            if diff_text is None:
                diff_text = os.getenv('CODE_DIFF_TEXT', '')
            
            changed_methods = {}
            if diff_text:
                try:
                    changed_methods = self._extract_changed_methods(repo_path, diff_text, java_files)
                    logger.info(
                        f"从diff中提取到方法级改动信息: "
                        f"{sum(len(v) for v in changed_methods.values())} 个方法"
                    )
                except Exception as e:
                    logger.error(f"从diff中提取方法级改动信息失败，将按文件级别分析: {e}")
                    changed_methods = {}
            
            # 调用Java工具（优先按方法级别，失败则退化为文件级）
            context_data = self._call_java_tool(repo_path, java_files, changed_methods or None)
            
            # 格式化为LLM友好的文本
            context_text = self._format_context(context_data)
            
            # 检查token数量
            token_count = count_tokens(context_text)
            logger.info(f"上下文token数量: {token_count}")
            
            if token_count > self.max_context_tokens:
                logger.warning(f"上下文token数量({token_count})超过限制({self.max_context_tokens})，进行截断")
                context_text = self._truncate_context(context_data, self.max_context_tokens)
            
            return context_text
            
        except Exception as e:
            logger.error(f"分析代码上下文失败: {e}")
            raise
    
    def _call_java_tool(
        self,
        repo_path: str,
        java_files: List[str],
        changed_methods: Optional[Dict[str, List[str]]] = None
    ) -> Dict[str, Any]:
        """
        调用Java工具提取上下文
        
        Args:
            repo_path: 仓库路径
            java_files: Java文件列表
            changed_methods: 变更方法映射，key为文件路径，value为方法名列表
        
        Returns:
            上下文数据（JSON）
        """
        try:
            # 构建classpath
            target_classes = self.java_tool_path / "target" / "classes"
            
            # 查找所有依赖jar包
            target_dependency = self.java_tool_path / "target" / "dependency"
            if target_dependency.exists():
                classpath = f"{target_classes}:{target_dependency}/*"
            else:
                # 如果没有dependency目录，需要先下载依赖
                logger.info("依赖jar包不存在，开始下载...")
                subprocess.run(
                    ["mvn", "dependency:copy-dependencies"],
                    cwd=str(self.java_tool_path),
                    capture_output=True,
                    timeout=300
                )
                classpath = f"{target_classes}:{target_dependency}/*"
            
            # 构建命令
            cmd = [
                'java', '-cp', classpath,
                'com.codereview.ContextExtractor',
                '--repo-path', repo_path,
                '--changed-files', ','.join(java_files)
            ]

            # 如果有方法级别的改动信息，作为JSON传给Java侧
            if changed_methods:
                try:
                    changed_methods_json = json.dumps(changed_methods, ensure_ascii=False)
                    cmd.extend(['--changed-methods', changed_methods_json])
                except Exception as e:
                    logger.error(f"序列化changed_methods失败，将按文件级别分析: {e}")
            
            logger.info(f"执行Java命令: java -cp ... ContextExtractor --repo-path {repo_path} --changed-files {','.join(java_files)}")
            
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=60
            )
            
            if result.returncode != 0:
                logger.error(f"Java工具执行失败:\nSTDOUT: {result.stdout}\nSTDERR: {result.stderr}")
                raise Exception(f"Java工具执行失败: {result.stderr}")
            
            # 解析JSON输出
            context_data = json.loads(result.stdout)
            logger.info(f"成功提取上下文: {len(context_data.get('changedFiles', []))} 个修改文件, "
                       f"{len(context_data.get('relatedFiles', []))} 个相关文件")
            
            return context_data
            
        except subprocess.TimeoutExpired:
            logger.error("Java工具执行超时（60秒）")
            raise Exception("Java工具执行超时")
        except json.JSONDecodeError as e:
            logger.error(f"解析Java工具输出失败: {e}\nSTDOUT: {result.stdout}")
            raise Exception(f"解析Java工具输出失败: {e}")

    def _extract_changed_methods(
        self,
        repo_path: str,
        diff_text: str,
        java_files: List[str]
    ) -> Dict[str, List[str]]:
        """
        从git diff文本中粗略提取“发生改动的方法名”
        
        设计说明：
        - 先用diff的文件块（diff --git a/... b/...）定位到对应的Java文件
        - 在对应文件的diff hunk中，通过正则匹配新增/修改的“方法声明行”来提取方法名
        - 这是一个启发式实现：如果无法识别出方法，则该文件退化为“整文件都算改动”
        """
        # 只保留本次变更涉及的 Java 文件，便于快速匹配 diff 块
        file_set = {f.strip() for f in java_files}
        # 文件 -> 方法名列表
        changed_methods: Dict[str, List[str]] = {}
        # 源码行缓存，避免重复读文件
        file_cache: Dict[str, List[str]] = {}

        import re as _re
        method_pattern = _re.compile(
            r'\b(public|protected|private|static|final|synchronized|abstract|default)\b'
            r'[^()]*?\b([A-Za-z_][A-Za-z0-9_]*)\s*\('
        )

        def add_method(path: str, name: str):
            changed_methods.setdefault(path, [])
            if name not in changed_methods[path]:
                changed_methods[path].append(name)

        def backfill(path: str, lineno: Optional[int]) -> Optional[str]:
            """
            仅改方法体时，diff 里没有方法声明行。
            这里用「新文件行号」回溯源码向上最多200行，找到最近的方法声明。
            """
            if lineno is None:
                return None
            try:
                if path not in file_cache:
                    abs_path = Path(repo_path) / path  # 组装绝对路径
                    with open(abs_path, 'r', encoding='utf-8', errors='ignore') as f:
                        file_cache[path] = f.readlines()  # 缓存文件内容
                lines = file_cache[path]
                idx = min(len(lines), lineno) - 1  # 转为 0-based 行号
                # 自下而上回溯 200 行内的最近方法声明
                for i in range(idx, max(-1, idx - 200), -1):
                    m_local = method_pattern.search(lines[i])
                    if m_local:
                        return m_local.group(2)
            except Exception:
                return None
            return None

        for line in diff_text.splitlines():
            # 识别新的文件diff块
            if line.startswith('diff --git'):
                parts = line.split()
                if len(parts) >= 4:
                    # 形如: diff --git a/src/... b/src/...
                    b_path = parts[3]
                    if b_path.startswith('b/'):
                        b_path = b_path[2:]
                    current_file = b_path if b_path in file_set else None
                else:
                    current_file = None
                current_method = None
                current_new_lineno = None
                continue

            if not current_file:
                continue

            # 新的hunk开始时重置方法上下文，避免跨hunk串联
            if line.startswith('@@'):
                current_method = None
                # 解析hunk的新文件起始行号，例如: @@ -10,5 +20,8 @@
                try:
                    plus_part = line.split('+', 1)[1]
                    start_str = plus_part.split('@@', 1)[0].strip().split(',', 1)[0]
                    current_new_lineno = int(start_str)  # 记录新文件的起始行号
                except Exception:
                    current_new_lineno = None
                continue

            # 跳过文件头
            if line.startswith('+++') or line.startswith('---'):
                continue

            if not line:
                continue

            prefix = line[0]
            if prefix not in ('+', '-', ' '):
                continue

            code = line[1:].strip()

            # 更新新文件的行号：只有'+'或' '会增加新文件行号；'-'不增加
            if prefix in ('+', ' ') and current_new_lineno is not None:
                current_new_lineno += 1  # 对应新文件的行号前进

            m = method_pattern.search(code)
            if m:
                current_method = m.group(2)
                if prefix == '+':
                    add_method(current_file, current_method)
                continue

            # 方法体新增时，尝试复用/回溯方法名
            if prefix == '+':
                target = current_method or backfill(current_file, current_new_lineno)  # 先用上下文方法，再回溯源码
                if target:
                    add_method(current_file, target)

        return changed_methods
    
    def _format_context(self, data: Dict[str, Any]) -> str:
        """
        格式化为LLM友好的文本
        
        Args:
            data: 上下文数据
        
        Returns:
            格式化的文本
        """
        context_parts = []
        
        context_parts.append("\n\n【代码上下文（仅供参考，不要对此部分提问题）】\n")
        
        # 1. 修改文件的完整内容
        changed_files = data.get('changedFiles', [])
        if changed_files:
            context_parts.append("\n== 修改文件的完整内容 ==\n")
            for file_ctx in changed_files:
                context_parts.append(f"\n文件: {file_ctx['path']}")
                if file_ctx.get('className'):
                    context_parts.append(f"类名: {file_ctx['className']}")
                if file_ctx.get('annotations'):
                    context_parts.append(f"类注解: {', '.join(file_ctx['annotations'])}")
                context_parts.append(f"\n{file_ctx['fullContent']}\n")
        
        # 2. 相关依赖类
        related_files = data.get('relatedFiles', [])
        if related_files:
            context_parts.append("\n== 相关依赖类 ==\n")
            for related_file in related_files:
                context_parts.append(f"\n文件: {related_file['path']} ({related_file['reason']})")
                context_parts.append(f"\n{related_file['fullContent']}\n")
        
        # 3. 方法调用链
        call_chains = data.get('callChains', [])
        if call_chains:
            context_parts.append("\n== 方法调用分析 ==\n")
            for chain in call_chains[:20]:  # 最多20条
                context_parts.append(f"- {chain}\n")
        
        return "".join(context_parts)
    
    def _truncate_context(self, data: Dict[str, Any], max_tokens: int) -> str:
        """
        截断上下文以满足token限制
        
        策略：
        1. 优先保留修改文件的完整内容
        2. 相关文件只保留类定义和方法签名，不保留方法体
        3. 调用链保留前10条
        
        Args:
            data: 上下文数据
            max_tokens: 最大token数
        
        Returns:
            截断后的文本
        """
        context_parts = []
        
        context_parts.append("\n\n【代码上下文（仅供参考，不要对此部分提问题）】\n")
        context_parts.append("注意：由于token限制，部分内容已截断\n")
        
        # 1. 修改文件的完整内容（优先保留）
        changed_files = data.get('changedFiles', [])
        if changed_files:
            context_parts.append("\n== 修改文件的完整内容 ==\n")
            for file_ctx in changed_files:
                context_parts.append(f"\n文件: {file_ctx['path']}\n")
                context_parts.append(f"{file_ctx['fullContent']}\n")
        
        # 检查是否还有空间
        current_text = "".join(context_parts)
        current_tokens = count_tokens(current_text)
        
        if current_tokens >= max_tokens:
            # 修改文件本身就超限了，只能截断
            from biz.utils.token_util import truncate_text_by_tokens
            return truncate_text_by_tokens(current_text, max_tokens)
        
        # 2. 相关依赖类（简化版，只包含类名和方法签名）
        related_files = data.get('relatedFiles', [])
        if related_files and current_tokens < max_tokens * 0.8:
            context_parts.append("\n== 相关依赖类（简化） ==\n")
            for related_file in related_files[:3]:  # 最多3个相关文件
                context_parts.append(f"\n文件: {related_file['path']}")
                # 只包含文件路径，不包含完整内容
                context_parts.append(" (详细内容已省略)\n")
        
        # 3. 方法调用链
        call_chains = data.get('callChains', [])
        if call_chains and current_tokens < max_tokens * 0.9:
            context_parts.append("\n== 方法调用分析 ==\n")
            for chain in call_chains[:10]:  # 最多10条
                context_parts.append(f"- {chain}\n")
        
        return "".join(context_parts)


