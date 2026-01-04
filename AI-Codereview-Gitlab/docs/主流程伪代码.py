"""
代码评审服务主流程伪代码
基于生产环境 merge_service.py 还原

作者: 根据截图还原
日期: 2025-12-30
"""

from typing import List, Dict, Tuple
from dataclasses import dataclass
import time
import traceback
import re


# ============================================================================
# 数据结构定义
# ============================================================================

@dataclass
class AgentResult:
    """单个Agent的评审结果"""
    review_result: str          # 原始评审文本（包含思考过程）
    question_list: List         # 问题列表
    question_list_str: str      # 问题列表字符串格式
    overall_score: Score        # 总体分数对象
    need_recheck: bool          # 是否需要复核
    extract_json: str           # 提取的JSON结构化数据


@dataclass
class Score:
    """分数对象"""
    value: int                  # 分数值 0-10
    confidence: int             # 置信度


# ============================================================================
# 1. 顶层入口函数
# ============================================================================

def code_review(handler, is_add_note=None, is_send_msg=None):
    """
    MR代码评审主入口
    
    工作流程:
    1. 获取commits和changes
    2. 调用核心评审逻辑
    3. 生成GitLab备注
    4. 记录任务状态
    
    :param handler: MergeRequestHandler对象
    :param is_add_note: 是否添加评审备注到GitLab
    :param is_send_msg: 是否发送消息通知
    """
    
    # Step 1: 设置trace_id用于日志追踪
    trace_id = generate_trace_id_from_mr(handler.webhook_data)
    set_trace_id(trace_id)
    
    # Step 2: 判断是否为测试模式
    if is_add_note is None:
        note_need_add = os.getenv("NOTE_NEED_ADD", True)
        if note_need_add:
            is_add_note = True
        else:
            is_add_note = note_need_add.lower() == 'true'
    
    # Step 3: 保存任务信息
    task = handler.task
    if not task:
        task = task_service.save_merge_task(
            handler.execute_id, 
            handler.execute_type,
            execute_status='EXECUTING',
            handler.webhook_data, 
            is_add_note, 
            is_send_msg
        )
    
    error_info = ""
    
    try:
        # Step 4: 获取commits
        commits = handler.get_merge_request_commits()
        if not commits:
            _skip_task(task, error_info: "未获取到commit信息", handler, is_add_note)
            return
        
        # Step 5: 检查并过滤changes
        cr_changes = filter_and_handle_changes(changes)
        if not cr_changes:
            _skip_task(task, error_info: "未检测到需要REVIEW的代码", handler, is_add_note)
            return
        
        # Step 6: 核心评审处理
        agent_results_list, merge_results, error_info = handle_mr_code(
            handler, 
            cr_changes, 
            commits, 
            formatted_changes
        )
        
        # Step 7: 添加GitLab评审备注
        if is_add_note and handler.action != 'test':
            notes = _build_gitlab_notes(agent_results_list, merge_results)
            handler.add_merge_request_notes(''.join(notes))
        else:
            logger.info(f"Merge Request Hook event, action={handler.action}, 不进行评审，只更新信息。*")
            update_statistics_rating_merge_status(handler.webhook_data)
        
        # Step 8: 错误处理
        if error_info:
            fail_task_info(task.task_id, error_info)
        else:
            success_task_info(task.task_id)
    
    except Exception as e:
        error_message = f'AI Code Review 服务出现未知错误: {str(e)}\n{traceback.format_exc()}'
        logger.error('出现未知错误: %s', error_message)
        if task:
            fail_task_info(task.task_id, error_message)


# ============================================================================
# 2. 核心处理函数
# ============================================================================

def handle_mr_code(handler, original_changes=None, commits=None, 
                   formatted_changes: list = None) -> Tuple[List, Dict, str]:
    """
    处理MR代码的核心逻辑 - 多Agent并行评审
    
    处理流程:
    1. 代码分批（大diff拆分成多个小batch）
    2. 每个batch遍历所有agent类型（general, security等）
    3. 每个agent独立评审并解析结果
    4. 如果有需要recheck的，进行二次评审
    5. 合并所有结果
    
    :param handler: MergeRequestHandler
    :param original_changes: 原始代码变更
    :param commits: 提交列表
    :param formatted_changes: 格式化后的变更
    :return: (agent_results_list, merge_results, error_info)
    """
    
    # 初始化
    if formatted_changes is None:
        formatted_changes = []
    
    task = handler.task
    small_client = CodeReviewer(handler.client)
    
    # 记录时间
    start_time = time.time()
    
    # Step 1: 代码分批处理（避免单次请求过长）
    cr_changes_list = split_changes(
        original_changes, 
        int(os.getenv('CONTENT_MAX_LENGTH', 5000))
    )
    
    # 构建提交信息
    commits_text = ';'.join([commit['title'] for commit in commits])
    agent_results_list = []
    
    # 是否启用思考链（多批次时禁用以节省token）
    include_thought_chain = False
    error_info = ""
    batch_num = len(cr_changes_list)
    
    if batch_num > 1:
        include_thought_chain = False
    
    # Step 2: 遍历每个代码批次
    for i, cr_changes in enumerate(cr_changes_list[:max_batch]):
        agent_results = {}
        ast_content = None
        need_recheck = False
        agent_type = ""
        batch_id = str(i + 1)
        
        logger.info(f"开始分片评审处理, 共 {batch_num} 个分片, 当前处理第 {batch_id} 片")
        
        # Step 3: 遍历每种Agent类型
        for agent_type in agent_types:
            
            # Step 3.1: 如果需要AST上下文（可选）
            if ast_content is None and os.getenv('AGENT_AST_' + agent_type.upper(), 'false').lower() == 'true':
                # 获取AST上下文的逻辑
                # ast_content = get_ast_context(...)
                pass
            
            # Step 3.2: 调用LLM进行评审
            review_result, error_info = get_review_result(
                small_client,
                diffs_text=str(cr_changes),
                commits_text=commits_text,
                agent_type=agent_type,
                include_thought_chain=include_thought_chain,
                ast_content=ast_content,
                batch_num=batch_num,
                batch_id=batch_id,
                error_info=error_info,
                agent_results=agent_results
            )
            
            # Step 3.3: 解析评审结果
            agent_results[agent_type] = parse_review_result(
                review_result=review_result,
                task=task,
                agent_type=agent_type,
                batch_num=batch_num,
                batch_id=batch_id
            )
            
            # Step 3.4: 检查是否需要recheck
            if not need_recheck:
                need_recheck = agent_results[agent_type].need_recheck
        
        logger.info(
            f"完成分片评审处理, 共 {batch_num} 个分片, "
            f"当前处理第 {batch_id} 片, Agent results: {str(agent_results)}"
        )
        
        agent_results_list.append(agent_results)
    
    # Step 4: 如果需要recheck，进行综合评审（可选）
    if need_recheck:
        # 构建二次评审的提示词
        think_client = get_think_client()
        
        # 构建汇总提示
        prompt = "请对以下多个分片的评审结果进行综合分析。\n\n"
        
        for i, agent_results in enumerate(agent_results_list):
            recheck_result = agent_results.get('recheck')
            if recheck_result:
                prompt += f"第{i+1}个分片的评审结果: {recheck_result.review_result}\n\n*"
        
        prompt += """请综合所有分片的评审结果, 最终给出精炼的评审
请以JSON格式返回结果***"""
        
        try:
            # 调用大模型进行汇总
            merged_result = think_client.direct_review(prompt)
            score = ""
            logger.info(f"大模型汇总结果: {merged_result}")
            
            if not merged_result:
                logger.warning("合并评审结果为空")
                return {
                    "status": "1",
                    "result": "合并评审结果为空, 请检查大模型返回内容",
                    "score": ""
                }
            
            try:
                score = rating_parse_util.extract_score_v2(merged_result).score
            except Exception as e:
                logger.warning(f"解析汇总结果失败: {str(e)}")
            
            logger.info(f"汇总结果: {merged_result}")
            return {
                "status": "0",
                "result": merged_result,
                "score": score
            }
        
        except Exception as e:
            logger.error(f"合并评审结果失败: {str(e)}")
            return {
                "status": "1",
                "result": "汇总评审结果失败, 参考评审过程",
                "score": ""
            }
    
    # Step 5: 合并多个Agent的评审结果
    merge_results = merge_agent_results(agent_results_list)
    end_time = time.time()
    
    # Step 6: 保存评审结果
    save_parse_result(
        agent_results_list,
        cr_changes_list,
        task,
        start_time,
        end_time,
        handler,
        merge_results
    )
    
    return agent_results_list, merge_results, error_info


# ============================================================================
# 3. LLM调用函数
# ============================================================================

def get_review_result(client, diffs_text, commits_text, agent_type,
                     include_thought_chain, ast_content, batch_num,
                     batch_id, error_info, agent_results=None):
    """
    调用LLM进行代码评审
    
    :param client: CodeReviewer客户端
    :param diffs_text: diff格式的代码
    :param commits_text: 提交信息
    :param agent_type: Agent类型(general/security/recheck)
    :param include_thought_chain: 是否包含思考链
    :param ast_content: AST上下文（可选）
    :param batch_num: 总批次数
    :param batch_id: 当前批次ID
    :param error_info: 累积的错误信息
    :param agent_results: 其他Agent的结果（用于recheck）
    :return: (review_result, error_info)
    """
    try:
        # 关键调用点：这是测试框架需要复用的核心方法
        review_result = client.review_code(
            diffs_text=str(diffs_text),
            commits_text=commits_text,
            agent_type=agent_type,
            include_thought_chain=include_thought_chain,
            ast_content=ast_content,
            agent_results=agent_results
        )
        
        return review_result, error_info
    
    except Exception as e:
        stack_trace = traceback.format_exc()
        
        tmp_error_info = (
            f"\n大模型请求失败, 共 {batch_num} 个分片, "
            f"当前Agent类型: {agent_type}, 当前处理第 {batch_id} 片"
        )
        error_info += tmp_error_info
        logger.error(tmp_error_info + stack_trace)
        
        return review_result, error_info


# ============================================================================
# 4. 结果解析函数
# ============================================================================

def parse_review_result(review_result, task, agent_type, batch_num, batch_id):
    """
    解析LLM返回的评审结果
    
    处理逻辑:
    1. 提取JSON结构化数据
    2. 解析问题列表
    3. 提取分数
    4. 判断是否需要recheck
    
    :param review_result: LLM返回的原始文本
    :param task: 任务对象
    :param agent_type: Agent类型
    :param batch_num: 批次总数
    :param batch_id: 当前批次ID
    :return: AgentResult对象
    """
    
    if not review_result:
        logger.warning(
            f"review结果为空, 直接返回缺认结果, "
            f"共 {batch_num} 个分片, 当前处理第 {batch_id} 片"
        )
        return AgentResult(
            review_result=review_result,
            question_list=[],
            question_list_str="",
            overall_score=Score(-1, -1),
            need_recheck=False,
            extract_json=""
        )
    
    # 初始化
    question_list = []
    need_recheck = False
    extract_json = ""
    question_list_str = ""
    overall_score = Score(-1, -1)
    
    if extract_json:
        # 从JSON中解析问题列表
        question_list = Question.parse_rating_questions(
            task_id=task.task_id,
            rating_info=review_result,
            agent_type=agent_type,
            batch_id=str(batch_id),
            extract_json=extract_json
        )
        overall_score = rating_parse_util.extract_score_v2(extract_json)
    
    if question_list:
        need_recheck = True
        question_list_str += '\n'.join([str(q) for q in question_list])
    
    logger.info(
        f"提取json数据, 共 {batch_num} 个分片, agent_type: {agent_type}, "
        f"当前处理第 {batch_id} 片, json: {json.dumps(extract_json, separators=(',', ':'))}"
    )
    
    return AgentResult(
        review_result=review_result,
        question_list=question_list,
        question_list_str=question_list_str,
        overall_score=overall_score,
        need_recheck=need_recheck,
        extract_json=extract_json
    )


# ============================================================================
# 5. 结果合并函数
# ============================================================================

def merge_agent_results(agent_results_list: List[Dict]) -> Dict:
    """
    合并多个Agent的评审结果
    
    合并策略:
    1. 如果没有recheck结果 -> 全部通过
    2. 如果只有1个recheck结果 -> 直接使用
    3. 如果有多个recheck结果 -> 需要调用大模型汇总
    
    :param agent_results_list: 多个批次的Agent结果列表
    :return: 合并后的结果字典 {status, result, score}
    """
    
    # 收集所有recheck结果
    recheck_results = []
    
    for agent_results in agent_results_list:
        recheck_result = agent_results.get('recheck')
        if recheck_result:
            recheck_results.append(recheck_result)
    
    if not recheck_results:
        # 情况1: 没有需要复核的问题，全部通过
        return {
            "status": "0",
            "result": "通过通过",
            "score": "10"
        }
    
    if len(recheck_results) == 1:
        # 情况2: 只有一个复核结果，直接返回
        recheck_result = recheck_results[0]
        if recheck_result and hasattr(recheck_result, 'extract_json'):
            return {
                "status": "0",
                "result": recheck_result.extract_json,
                "score": recheck_result.score
            }
    
    else:
        # 情况3: 多个复核结果需要合并
        return {
            "status": "1",
            "result": "汇总评审结果暂未完成, 参考评审过程",
            "score": ""
        }
    
    except Exception as e:
        logger.error(f"合并评审结果失败: {str(e)}")
        return {
            "status": "1",
            "result": "汇总评审结果失败, 参考评审过程",
            "score": ""
        }


# ============================================================================
# 6. 结果持久化函数
# ============================================================================

def save_parse_result(agent_results_list, cr_changes_list, task,
                     start_time, end_time, handler, merge_results):
    """
    保存评审结果到数据库
    
    保存内容:
    1. 各维度的评审内容（general, security, recheck）
    2. 分数统计
    3. Token使用量
    4. 时间统计
    
    :param agent_results_list: Agent结果列表
    :param cr_changes_list: 代码变更列表
    :param task: 任务对象
    :param start_time: 开始时间
    :param end_time: 结束时间
    :param handler: MergeRequestHandler
    :param merge_results: 合并后的结果
    """
    
    total_content = ""
    total_token = 0
    
    # 计算总内容和token
    for cr_changes in cr_changes_list[:max_batch]:
        content_str = str(cr_changes)
        total_content += content_str
        total_token += token_length(content_str)
    
    content_size = len(total_content)
    token_size = total_token
    send_content_size = content_size
    send_token_size = token_size
    
    # 提取各维度的评审内容
    general_contents = []
    general_score = 10
    general_questions = []
    
    security_contents = []
    security_questions = []
    security_score = 10
    
    recheck_contents = []
    recheck_questions = None  # None表示没有提问题
    recheck_score = 10
    
    # 遍历所有Agent结果
    for agent_results in agent_results_list:
        general_res = agent_results.get('general')
        security_res = agent_results.get('security')
        recheck_res = agent_results.get('recheck')
        
        if general_res:
            general_contents.append(general_res.review_result)
            general_questions.extend(general_res.question_list)
            general_score = min(general_score, general_res.score)
        
        if security_res:
            security_contents.append(security_res.review_result)
            security_questions.extend(security_res.question_list)
            security_score = min(security_score, security_res.score)
        
        if recheck_res:
            if recheck_res.question_list is not None:
                recheck_questions = []
            recheck_contents.append(recheck_res.review_result)
            
            if recheck_res.question_list is not None:
                recheck_questions.extend(recheck_res.question_list)
            
            if merge_results.get('score'):
                recheck_score = merge_results.get('score')
            else:
                recheck_score = min(recheck_score, recheck_res.score)
    
    # 构建完整的评审内容
    merge_content = (
        '\n'.join(security_contents), security_questions,
        '\n'.join(recheck_contents), recheck_questions,
        merge_content, total_score, general_score, security_score, recheck_score,
        start_time, end_time,
        ExtendData(handler.client.model_name(), content_size, send_content_size,
                  token_size, send_token_size)
    )
    
    logger.info(f"保存merge rating结果, task_id: {task.task_id}")


# ============================================================================
# 7. GitLab备注生成函数
# ============================================================================

def _build_gitlab_notes(agent_results_list, merge_results: str = "") -> List[str]:
    """
    构建GitLab注释内容
    
    备注结构:
    1. 评审结果和总分
    2. 思考过程（如果有）
    3. 各批次的详细结果
    4. 各Agent的评审内容
    
    :param agent_results_list: Agent结果列表
    :param merge_results: 合并后的结果
    :return: 备注字符串列表
    """
    
    notes = []
    result_value = merge_results.get("result", "")
    merge_score = merge_results.get("score", "**")
    think_content = ""
    post_think = ""
    
    if not result_value:
        logger.error(f"空的汇总评审结果")
        result_value = "合并评审结果为空, 请检查大模型返回内容"
    
    if result_value:
        # 提取 <think> 标签内容
        think_match = re.search(
            r'<think>(.*?)</think>',
            result_value,
            re.DOTALL
        )
        if think_match:
            think_content = think_match.group(1).strip()
            # 提取 </think> 之后的内容
            end_think_match = re.search(
                r'</think>(.*?)$',
                result_value,
                re.DOTALL
            )
            if end_think_match:
                post_think = end_think_match.group(1).strip()
        else:
            # 没有 <think> 标签，全部算作 post_think
            think_content = ''
            post_think = result_value.strip()
    
    # === 评审结果 ===
    notes.append(f"# 📊 评审结果: ")
    
    if merge_score:
        notes.append(f"{merge_score}分\n\n")
    else:
        notes.append(f"\n\n")
    
    if post_think:
        # 确保JSON格式正确
        has_code_block = re.match(
            r'^```json\n.*?\n```$',
            post_think,
            re.DOTALL
        ) is not None
        
        if not has_code_block:
            post_think = post_think.replace("```", "\\'\\'")
            post_think = f"```json\n{post_think}\n```"
        
        stripped_post = post_think.rstrip('\n')
        if not stripped_post.endswith('```'):
            post_think += '\n```'
        
        notes.append(f"{post_think}\n\n(~-~ * 80)\n\n")
    else:
        notes.append(f"\n\n")
    
    # === 思考过程 ===
    if think_content:
        notes.append(f"### 🚀 详细过程: \n\n {think_content}\n\n")
    
    # === 各批次详情 ===
    notes.append(
        f"## 🎯 本地代码评审共 {len(agent_results_list)} 组, "
        f"可按查看各组内的详情\n\n"
    )
    
    # 逐一处理各批次的Agent结果
    for i, agent_results in enumerate(agent_results_list):
        
        notes.append(f"\n\n## 📌 第{i+1} 组: \n\n")
        
        # 检查是否有 recheck 内容
        recheck = agent_results.get('recheck', None)
        if recheck:
            content = recheck.review_result
            if content:
                content = content.replace("<think>", "### 💭思考过程详细\n\n")
                content = content.replace("</think>", "### 最终整体思考结束\n\n")
                
                notes.append(f"# ✅ 复核结果: \n\n{content}")
        
        # 处理其他 agent (general, security 等)
        for agent, AgentResult in agent_results.items():
            if agent == "recheck":
                continue  # recheck 已经处理过
            
            if AgentResult:
                notes.append(
                    rating_parse_util.build_gitlab_note(
                        AgentResult.review_result,
                        AgentResult.question_list,
                        agent
                    )
                )
    
    notes.append("\n</details>")
    return notes


# ============================================================================
# 8. 辅助函数
# ============================================================================

def _skip_task(task, error_info: str, handler, is_add_note):
    """
    跳过任务的统一处理
    
    :param task: 任务对象
    :param error_info: 错误信息
    :param handler: MergeRequestHandler
    :param is_add_note: 是否添加备注
    """
    fail_task_info(task.task_id, error_info)
    if is_add_note and handler.action != 'test':
        handler.add_merge_request_notes(f"## {error_info}, 此次评审过过程; 请正常合并")


def get_think_client():
    """获取思考客户端（用于recheck汇总）"""
    global __think_client
    if __think_client is None:
        # 使用增强的配置参数初始化思考客户端
        base_url = os.getenv("THINK_MODEL_API_BASE_URL")
        model_name = os.getenv("THINK_MODEL_API_MODEL")
        api_key = os.getenv("THINK_MODEL_API_KEY")
        
        # 内建链式思考能力
        model_params = ModelParamsInfo(
            base_url=base_url,
            mode_name=model_name,
            api_key=api_key,
            extra_body={"chat_template_kwargs": {"enable_thinking": True}}
        )
        
        openai_client = OpenAIClient(model_params_info=model_params)
        __think_client = CodeReviewer(openai_client)
    
    return __think_client


# ============================================================================
# 配置和常量
# ============================================================================

# Agent类型列表
agent_types = os.getenv('AGENT_TYPES', 'general').split(',')

# 最大批次限制
max_batch = int(os.getenv('MAX_BATCH', 1))

# 全局变量
__think_client = None


# ============================================================================
# 测试框架对接示例
# ============================================================================

def test_review_with_testcase():
    """
    测试框架如何复用这些函数
    """
    
    # Step 1: 准备测试数据
    diff_content = """
diff --git a/UserMapper.java b/UserMapper.java
@@ -3,4 +3,8 @@
     @Select("SELECT * FROM users WHERE status = 'active'")
     List<User> findActiveUsers();
+    
+    @Select("SELECT * FROM users WHERE username = '${username}'")
+    User findByUsername(@Param("username") String username);
 }
"""
    
    # Step 2: 初始化CodeReviewer（复用生产代码）
    from biz.utils.code_reviewer import CodeReviewer
    reviewer = CodeReviewer()
    
    # Step 3: 直接调用评审方法
    result = reviewer.review_code(
        diffs_text=diff_content,
        commits_text="测试用例",
        agent_type="general",
        include_thought_chain=False,
        ast_content=None,
        agent_results=None
    )
    
    # Step 4: 解析结果
    parsed_result = parse_review_result(
        review_result=result,
        task=mock_task,
        agent_type="general",
        batch_num=1,
        batch_id="1"
    )
    
    # Step 5: 验证结果
    assert parsed_result.need_recheck == True
    assert "SQL注入" in parsed_result.review_result
    
    print(f"测试通过！检出问题: {len(parsed_result.question_list)} 个")


if __name__ == "__main__":
    print("""
    代码评审服务主流程说明
    =====================
    
    1. 入口: code_review() - 接收Webhook
    2. 核心: handle_mr_code() - 多Agent并行评审
    3. 评审: get_review_result() - 调用LLM
    4. 解析: parse_review_result() - 提取结构化数据
    5. 合并: merge_agent_results() - 汇总结果
    6. 输出: _build_gitlab_notes() - 生成备注
    
    测试框架对接点:
    - 直接复用 CodeReviewer.review_code()
    - 输入: diff格式字符串
    - 输出: JSON格式评审结果
    """)

