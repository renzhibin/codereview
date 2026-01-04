import logging
import traceback
from typing import List, Dict, Any, Tuple
from biz.utils.code_reviewer import CodeReviewer

logger = logging.getLogger(__name__)

class MergeService:
    def __init__(self):
        self.code_reviewer = CodeReviewer()

    def get_review_result(self, diff_text: str, commits_text: str, context: str = "") -> Tuple[str, str]:
        """
        调用 LLM 进行代码评审
        """
        try:
            review_result = self.code_reviewer.review_code(
                diffs_text=diff_text,
                commits_text=commits_text,
                context_section=context
            )
            return review_result, ""
        except Exception as e:
            logger.error(f"LLM request failed: {e}")
            return "", str(e)

    def parse_review_result(self, review_result: str) -> Dict[str, Any]:
        """
        解析评审结果
        """
        try:
            # 复用 CodeReviewer 中的解析逻辑
            parsed = self.code_reviewer.parse_review_result(review_result)
            return parsed
        except Exception as e:
            logger.warning(f"Parse review result failed: {e}")
            return {"score": 0, "issues": []}

    def review_merge_request(self, diff_text: str, commits_text: str = "", context: str = "") -> Dict[str, Any]:
        """
        执行合并请求评审流程 (模拟 handle_mr_code 的核心逻辑)
        """
        # 1. 获取评审结果
        review_result, error_info = self.get_review_result(diff_text, commits_text, context)
        
        if error_info:
            return {
                "question_list": [],
                "score": 0,
                "review_result": f"Error: {error_info}",
                "raw_result": ""
            }

        # 2. 解析评审结果
        parsed_result = self.parse_review_result(review_result)
        
        issues = parsed_result.get("issues", [])
        score = parsed_result.get("score", 0)

        # 转换为问题清单 (List[str])
        question_list = []
        for issue in issues:
            if isinstance(issue, dict):
                # 优先获取"问题描述"，其次是整个对象
                desc = issue.get("问题描述") or str(issue)
                question_list.append(desc)
            else:
                question_list.append(str(issue))

        return {
            "question_list": question_list,
            "score": score,
            "review_result": review_result,
            "raw_issues": issues
        }

