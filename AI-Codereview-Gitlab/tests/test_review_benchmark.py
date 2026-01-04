#!/usr/bin/env python3
"""
CRBench - 代码评审基准测试框架 (Code Review Benchmark Framework)

功能概述:
1. 自动化加载测试用例 (Qxx_维度/case_xxx)
2. 调用代码评审系统 (CodeReviewer) 进行评审
3. 解析评审结果 (JSON/文本) 并与预期结果 (metadata.json) 进行比对
4. 计算多维度指标: 准确率(Precision), 召回率(Recall), F1分数
5. 生成详细的测试报告 (JSON + 控制台摘要)

核心特性:
- 支持并发测试 (ThreadPoolExecutor)
- 兼容 JSON 和 文本 两种评审结果格式
- 支持模糊匹配和关键词匹配 (解决 LLM 输出不确定性问题)
- 详细的指标统计 (按维度、按安全/通用分类)

日期: 2025-12-30
"""

import os
import sys
import json
import time
import argparse
import re
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Any
from dataclasses import dataclass, asdict
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed

# ============================================================================
# 环境与路径配置
# ============================================================================

# 获取项目根目录 (假设当前文件在 tests/ 目录下，向上两级为项目根目录)
PROJECT_ROOT = Path(__file__).parent.parent
# 将项目根目录加入 sys.path，以便导入 biz 模块
sys.path.insert(0, str(PROJECT_ROOT))

# 导入基准测试专用配置
from benchmark_config import get_env_config, REVIEW_MODEL, MAX_WORKERS

# 加载并应用环境变量配置
ENV_CONFIG = get_env_config()
print(f"✅ 已加载配置 (模型: {REVIEW_MODEL}, 并发数: {MAX_WORKERS})")

# 基准测试根目录 (tests/ 目录)
BENCHMARK_DIR = Path(__file__).parent

# 设置日志目录和文件路径
log_dir = BENCHMARK_DIR / "log"
log_dir.mkdir(exist_ok=True)
os.environ["LOG_FILE"] = str(log_dir / "test_benchmark.log")

# 将配置注入环境变量，供 CodeReviewer 使用
for key, value in ENV_CONFIG.items():
    os.environ[key] = value

# 尝试导入业务代码
try:
    from biz.utils.code_reviewer import CodeReviewer
    from biz.service.merge_service import MergeService
    from biz.utils.log import logger
except ImportError as e:
    print(f"❌ 严重错误: 无法导入CodeReviewer模块: {e}")
    print("请确保在正确的项目环境下运行，且依赖已安装")
    sys.exit(1)


# ============================================================================
# 数据结构定义 (Data Structures)
# ============================================================================

@dataclass
class TestCase:
    """
    测试用例实体类
    对应 tests/Qxx_维度/case_xxx 目录下的一个测试场景
    """
    case_id: str          # 用例唯一标识，如 "case001_NPE"
    case_name: str        # 用例目录名
    dimension: str        # 所属维度，如 "Q01_Functionality"
    rule_id: str          # 关联的规则ID (可选)，如 "M001"
    severity: str         # 严重程度: critical, high, medium, low
    
    # 文件路径
    before_file: str      # 修改前的代码文件路径 (可选)
    after_file: str       # 修改后的代码文件路径 (用于上下文)
    diff_file: str        # diff补丁文件路径 (核心输入)
    commit_msg_file: str  # commit message文件路径 (上下文)
    metadata_file: str    # 元数据文件路径
    
    # 预期结果
    expected: Dict        # 包含 should_detect, target_issues 等预期信息


@dataclass
class TestResult:
    """
    单个测试用例的执行结果
    包含实际评审结果与预期结果的对比详情
    """
    case_id: str
    case_name: str
    dimension: str
    
    # 核心检测结果
    detected: bool                    # 实际是否检测到了问题
    should_detect: bool               # 预期是否应该检测到问题
    
    # 详细信息
    violations: List[str]             # 实际检测到的违规类型列表 (匹配后的)
    expected_issues: List[str]        # 预期包含的违规类型列表
    
    # 评分信息
    score: Optional[int]              # LLM 给出的代码评分 (0-10)
    
    # 判定矩阵 (Confusion Matrix Elements)
    is_correct: bool                  # 总体判定是否正确 (detected == should_detect)
    is_tp: bool                       # True Positive (正例预测正确): 有问题且测出了问题
    is_fp: bool                       # False Positive (假阳性/误报): 没问题却测出了问题
    is_fn: bool                       # False Negative (假阴性/漏报): 有问题却没测出问题
    is_tn: bool = False               # True Negative (真阴性): 没问题且确实没测出问题
    
    # 性能指标
    elapsed_time: float = 0.0         # 耗时 (秒)
    
    # 调试信息
    raw_output: str = ""              # LLM 的原始输出内容 (截断)


@dataclass
class BenchmarkReport:
    """
    基准测试最终报告
    包含所有汇总指标和详细结果
    """
    test_run: Dict                    # 运行元数据 (时间, 模型, 总数)
    overall_metrics: Dict             # 整体指标 (P, R, F1, Acc)
    by_dimension: Dict                # 按维度统计的指标
    general_review: Dict              # 通用评审指标 (Q01-Q05)
    security_review: Dict             # 安全评审指标 (Q06-Q07)
    failed_cases: List[Dict]          # 失败用例列表 (用于快速排查)
    all_results: List[Dict]           # 所有详细结果


# ============================================================================
# 1. 测试用例加载器 (Test Case Loader)
# ============================================================================

class TestCaseLoader:
    """
    负责从文件系统中扫描和加载测试用例
    """
    
    def __init__(self, benchmark_dir: str):
        self.benchmark_dir = Path(benchmark_dir)
    
    def load_all_cases(self) -> List[TestCase]:
        """
        加载所有维度的所有测试用例
        扫描规则: tests/Q*_* 目录下的所有用例
        """
        cases = []
        # 遍历所有维度目录 (以 Q 开头)
        for dimension_dir in sorted(self.benchmark_dir.glob("Q*_*")):
            if not dimension_dir.is_dir():
                continue
            
            # 加载该维度下的用例
            dimension_cases = self.load_dimension_cases(dimension_dir.name)
            cases.extend(dimension_cases)
        
        return cases
    
    def load_dimension_cases(self, dimension: str) -> List[TestCase]:
        """
        加载指定维度下的所有测试用例
        支持的目录模式: case*, positive*, trap*, real_trap*
        """
        cases = []
        dimension_dir = self.benchmark_dir / dimension
        
        if not dimension_dir.exists():
            return cases
        
        # 遍历支持的用例目录模式
        # negative*: 反例/负面用例 (应报警)
        # positive*: 正例/正面用例 (不应报警)
        for pattern in ["negative*", "positive*"]:
            for case_dir in sorted(dimension_dir.glob(pattern)):
                if not case_dir.is_dir():
                    continue
                
                try:
                    case = self._load_single_case(case_dir, dimension)
                    cases.append(case)
                except Exception as e:
                    print(f"警告: 加载用例失败 {case_dir.name}: {e}")
        
        return cases
    
    def _load_single_case(self, case_dir: Path, dimension: str) -> TestCase:
        """
        加载单个测试用例目录，读取 metadata.json 并构建 TestCase 对象
        """
        
        # 1. 读取 metadata.json (必须存在)
        metadata_file = case_dir / "metadata.json"
        if not metadata_file.exists():
            raise FileNotFoundError(f"metadata.json not found in {case_dir}")
        
        with open(metadata_file, 'r', encoding='utf-8') as f:
            config = json.load(f)
        
        # 2. 解析元数据字段
        case_id = config.get("case_id", case_dir.name)
        rule_id = config.get("rule_id", "")
        severity = config.get("severity", "warning")
        should_detect = config.get("should_detect", True)  # 默认为 True (应该检测出问题)
        target_issues = config.get("target_issues", [])    # 预期的具体问题列表
        
        # 3. 检查必要的文件是否存在
        before_file = case_dir / "before.java"
        after_file = case_dir / "after.java"
        diff_file = case_dir / "diff.patch"
        commit_msg_file = case_dir / "commit_msg.txt"
        
        if not diff_file.exists():
            raise FileNotFoundError(f"diff.patch not found")
        
        # 4. 构建预期结果字典
        expected = {
            "should_detect": should_detect,
            "target_issues": target_issues
        }
        
        return TestCase(
            case_id=case_id,
            case_name=case_dir.name,
            dimension=dimension,
            rule_id=rule_id,
            severity=severity,
            before_file=str(before_file),
            after_file=str(after_file),
            diff_file=str(diff_file),
            commit_msg_file=str(commit_msg_file) if commit_msg_file.exists() else "",
            metadata_file=str(metadata_file),
            expected=expected
        )


# ============================================================================
# 2. 评审系统适配器 (Adapter)
# ============================================================================

class ReviewSystemAdapter:
    """
    适配器模式：连接基准测试框架与真实的业务代码 (CodeReviewer)
    负责调用 review 接口并对结果进行标准化解析
    """
    
    def __init__(self):
        # 切换工作目录以确保能正确读取项目配置
        original_dir = os.getcwd()
        project_root = Path(__file__).parent.parent
        os.chdir(str(project_root))
        
        # 初始化真实的 MergeService 实例
        # 这里的 review_model 由环境变量控制
        self.merge_service = MergeService()
        self.reviewer = CodeReviewer() # Keep for static methods if needed, or remove if not used. 
        # Actually I can access static methods via class CodeReviewer directly if imported.
        print(f"✅ 已连接到 MergeService (模型: {REVIEW_MODEL})")
        
        os.chdir(original_dir)
    
    def review(self, test_case: TestCase) -> Tuple[str, Dict]:
        """
        执行单个用例的评审
        
        Returns:
            Tuple[str, Dict]: (原始输出字符串, 解析后的结构化结果)
        """
        
        try:
            # Step 1: 读取输入文件内容
            with open(test_case.diff_file, 'r', encoding='utf-8') as f:
                diff_content = f.read()
            
            commit_msg = ""
            if test_case.commit_msg_file and os.path.exists(test_case.commit_msg_file):
                with open(test_case.commit_msg_file, 'r', encoding='utf-8') as f:
                    commit_msg = f.read().strip()
            
            context_content = ""
            if os.path.exists(test_case.after_file):
                with open(test_case.after_file, 'r', encoding='utf-8') as f:
                    context_content = f.read()
            
            # Step 2: 调用 MergeService.review_merge_request() 核心业务方法
            service_result = self.merge_service.review_merge_request(
                diff_text=diff_content,
                commits_text=commit_msg or "代码修改",
                context=context_content   # 提供完整文件内容作为上下文
            )
            
            review_result = service_result.get("review_result", "")
                                                                                                                                                                                                                                  
            # Step 3: 解析 LLM 返回的非结构化/半结构化结果
            parsed_result = self._parse_review_result(service_result, test_case)
            
            return review_result, parsed_result
        
        except Exception as e:
            logger.error(f"评审失败 {test_case.case_id}: {e}", exc_info=True)
            print(f"❌ 评审失败 {test_case.case_id}: {e}")
            return str(e), {
                "error": str(e),
                "violations": [],
                "score": None
            }
    
    def _parse_review_result(self, service_result: Dict, test_case: TestCase) -> Dict:
        """
        解析评审结果，并匹配期望的问题
        """
        # 1. 从 MergeService 结果中获取
        score = service_result.get("score")
        issues = service_result.get("question_list", [])
        review_result = service_result.get("review_result", "")
        
        target_issues = test_case.expected.get("target_issues", [])
        matched_violations = []
        
        # 2. 检查解析出的问题是否命中预期 (精确+模糊匹配)
        for issue_desc in issues:
            for target in target_issues:
                # 使用 CodeReviewer 提供的匹配逻辑
                if CodeReviewer.check_issue_match(issue_desc, target):
                    if target not in matched_violations:
                        matched_violations.append(target)
        
        # 3. 兜底策略: 已移除
        
        # 4. 分数兜底
        if not score:
            # 如果发现了违规项，默认给 3 分；否则给 10 分
            score = 3 if matched_violations else 10
        
        return {
            "violations": matched_violations,
            "score": score,
            "summary": review_result[:500] if review_result else "",
            "matched_count": len(matched_violations)
        }



# ============================================================================
# 3. 结果评估器 (Evaluator)
# ============================================================================

class ResultEvaluator:
    """
    负责将解析后的结果与预期进行对比，生成最终的判定 (Pass/Fail)
    """
    
    @staticmethod
    def evaluate(test_case: TestCase, review_output: str, 
                 parsed_result: Dict, elapsed_time: float) -> TestResult:
        """
        评估单个测试用例
        
        Args:
            test_case: 用例定义
            review_output: LLM 原始输出
            parsed_result: 解析后的结构化数据
            elapsed_time: 耗时
        """
        
        # 获取预期值
        expected = test_case.expected
        should_detect = expected.get("should_detect", True)
        expected_issues = expected.get("target_issues", [])
        
        # 获取实际值
        actual_violations = parsed_result.get("violations", [])
        score = parsed_result.get("score")
        
        # 初步判定: 是否检测到了任意问题
        detected = len(actual_violations) > 0
        
        # 最终判定 (Confusion Matrix)
        is_correct = detected == should_detect
        
        is_tp = detected and should_detect          # True Positive: 应该报错且报错了 (正确召回)
        is_fp = detected and not should_detect      # False Positive: 不该报错却报错了 (误报)
        is_fn = not detected and should_detect      # False Negative: 应该报错却没报错 (漏报)
        is_tn = not detected and not should_detect  # True Negative: 不该报错且没报错 (正确通过)
        
        return TestResult(
            case_id=test_case.case_id,
            case_name=test_case.case_name,
            dimension=test_case.dimension,
            detected=detected,
            should_detect=should_detect,
            violations=actual_violations,
            expected_issues=expected_issues,
            score=score,
            is_correct=is_correct,
            is_tp=is_tp,
            is_fp=is_fp,
            is_fn=is_fn,
            is_tn=is_tn,
            elapsed_time=elapsed_time,
            raw_output=review_output[:1000]  # 只保留前1000字符以节省空间
        )


# ============================================================================
# 4. 指标计算器 (Metrics)
# ============================================================================

class MetricsCalculator:
    """
    计算统计指标 (Precision, Recall, F1, Accuracy)
    """
    
    @staticmethod
    def calculate_metrics(results: List[TestResult]) -> Dict:
        """
        计算一组结果的聚合指标
        """
        
        if not results:
            return {
                "total_cases": 0,
                "tp": 0, "fp": 0, "fn": 0, "tn": 0,
                "precision": 0, "recall": 0, "f1_score": 0, "accuracy": 0,
                "passed": 0, "failed": 0
            }
        
        # 统计各分类数量
        tp = sum(1 for r in results if r.is_tp)
        fp = sum(1 for r in results if r.is_fp)
        fn = sum(1 for r in results if r.is_fn)
        tn = sum(1 for r in results if r.is_tn)
        
        # 计算核心指标
        # Precision (查准率): 报出的问题中有多少是真问题? TP / (TP + FP)
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0
        
        # Recall (查全率/召回率): 应该发现的问题发现了多少? TP / (TP + FN)
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0
        
        # F1 Score: P和R的调和平均数
        f1_score = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0
        
        # Accuracy (准确率): 总体判断正确的比例
        accuracy = (tp + tn) / len(results) if len(results) > 0 else 0
        
        return {
            "total_cases": len(results),
            "tp": tp,
            "fp": fp,
            "fn": fn,
            "tn": tn,
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1_score": round(f1_score, 4),
            "accuracy": round(accuracy, 4),
            "passed": sum(1 for r in results if r.is_correct),
            "failed": sum(1 for r in results if not r.is_correct)
        }
    
    @staticmethod
    def calculate_by_dimension(results: List[TestResult]) -> Dict:
        """
        按维度分组计算指标
        """
        by_dimension = defaultdict(list)
        for result in results:
            by_dimension[result.dimension].append(result)
        
        dimension_metrics = {}
        for dimension, dim_results in by_dimension.items():
            dimension_metrics[dimension] = MetricsCalculator.calculate_metrics(dim_results)
        
        return dimension_metrics
    
    @staticmethod
    def calculate_by_category(results: List[TestResult]) -> Tuple[Dict, Dict]:
        """
        按大类(通用/安全)分组计算指标
        """
        general_dimensions = ["Q01_Functionality", "Q02_Security", 
                            "Q03_BestPractices", "Q04_Performance", "Q05_CodeStyle"]
        security_dimensions = ["Q06_HorizontalPrivilege", "Q07_VerticalPrivilege"]
        
        general_results = [r for r in results if r.dimension in general_dimensions]
        security_results = [r for r in results if r.dimension in security_dimensions]
        
        general_review = MetricsCalculator.calculate_metrics(general_results)
        general_review["dimensions"] = general_dimensions
        
        security_review = MetricsCalculator.calculate_metrics(security_results)
        security_review["dimensions"] = security_dimensions
        
        return general_review, security_review


# ============================================================================
# 5. 主测试框架 (Runner)
# ============================================================================

class BenchmarkRunner:
    """
    基准测试执行入口，协调各个组件
    """
    
    def __init__(self, benchmark_dir: str):
        self.benchmark_dir = benchmark_dir
        self.loader = TestCaseLoader(benchmark_dir)
        self.adapter = ReviewSystemAdapter()
        self.evaluator = ResultEvaluator()
        self.calculator = MetricsCalculator()
    
    def run_all(self, quick: bool = False, parallel: bool = True) -> BenchmarkReport:
        """
        执行全量测试
        Args:
            quick: 快速模式 (只跑前5个)
            parallel: 是否开启并发
        """
        
        print("🚀 加载测试用例...")
        cases = self.loader.load_all_cases()
        
        if quick:
            cases = cases[:5]
            print(f"⚡ 快速模式：测试 {len(cases)} 个用例")
        else:
            print(f"📊 共加载 {len(cases)} 个测试用例")
        
        if parallel and len(cases) > 1:
            print(f"🔀 并发模式：使用 {MAX_WORKERS} 个worker")
            results = self._run_parallel(cases)
        else:
            print("📝 串行模式")
            results = self._run_serial(cases)
        
        print("\n" + "=" * 80)
        print("📈 生成报告...")
        
        return self._generate_report(results)
    
    def _run_serial(self, cases: List[TestCase]) -> List[TestResult]:
        """
        串行执行所有用例 (用于调试)
        """
        results = []
        for i, case in enumerate(cases, 1):
            print(f"\n[{i}/{len(cases)}] 测试 {case.case_id}...")
            start_time = time.time()
            # 核心调用
            raw_output, parsed_result = self.adapter.review(case)
            elapsed_time = time.time() - start_time
            
            result = self.evaluator.evaluate(case, raw_output, parsed_result, elapsed_time)
            results.append(result)
            
            status = "✅ PASS" if result.is_correct else "❌ FAIL"
            print(f"   {status} | 检测: {result.detected} | 期望: {result.should_detect} | 耗时: {elapsed_time:.2f}s")
        return results
    
    def _run_parallel(self, cases: List[TestCase]) -> List[TestResult]:
        """
        并发执行所有用例 (用于生产/批量测试)
        使用 ThreadPoolExecutor
        """
        results = []
        completed = 0
        total = len(cases)
        
        def test_one_case(case: TestCase) -> TestResult:
            """单个用例的执行函数，运行在独立线程中"""
            start_time = time.time()
            raw_output, parsed_result = self.adapter.review(case)
            elapsed_time = time.time() - start_time
            return self.evaluator.evaluate(case, raw_output, parsed_result, elapsed_time)
        
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            # 提交所有任务
            future_to_case = {executor.submit(test_one_case, case): case for case in cases}
            
            # 处理完成的任务
            for future in as_completed(future_to_case):
                case = future_to_case[future]
                completed += 1
                try:
                    result = future.result()
                    results.append(result)
                    status = "✅ PASS" if result.is_correct else "❌ FAIL"
                    print(f"[{completed}/{total}] {case.case_id}: {status} (耗时: {result.elapsed_time:.2f}s)")
                except Exception as e:
                    print(f"[{completed}/{total}] {case.case_id}: ❌ ERROR - {e}")
        
        # 结果按 case_id 排序，保证输出顺序一致
        results.sort(key=lambda r: r.case_id)
        return results
    
    def run_dimension(self, dimension: str, parallel: bool = True) -> BenchmarkReport:
        """
        只运行指定维度的测试
        """
        print(f"🚀 加载维度: {dimension}")
        cases = self.loader.load_dimension_cases(dimension)
        print(f"📊 共加载 {len(cases)} 个测试用例")
        
        if parallel and len(cases) > 1:
            print(f"🔀 并发模式：使用 {MAX_WORKERS} 个worker")
            results = self._run_parallel(cases)
        else:
            print("📝 串行模式")
            results = self._run_serial(cases)
            
        return self._generate_report(results)
    
    def _generate_report(self, results: List[TestResult]) -> BenchmarkReport:
        """
        汇总结果并生成 BenchmarkReport 对象
        """
        overall_metrics = self.calculator.calculate_metrics(results)
        by_dimension = self.calculator.calculate_by_dimension(results)
        general_review, security_review = self.calculator.calculate_by_category(results)
        
        # 筛选出失败的用例，方便在报告中展示
        failed_cases = [
            {
                "case_id": r.case_id,
                "case_name": r.case_name,
                "dimension": r.dimension,
                "detected": r.detected,
                "should_detect": r.should_detect,
                "reason": "误报 (FP)" if r.is_fp else "漏报 (FN)" if r.is_fn else "未知"
            }
            for r in results if not r.is_correct
        ]
        
        return BenchmarkReport(
            test_run={
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ"),
                "total_cases": len(results),
                "model": REVIEW_MODEL
            },
            overall_metrics=overall_metrics,
            by_dimension=by_dimension,
            general_review=general_review,
            security_review=security_review,
            failed_cases=failed_cases,
            all_results=[asdict(r) for r in results]
        )


# ============================================================================
# 6. 报告生成器 (Report Output)
# ============================================================================

class ReportGenerator:
    """
    负责将 BenchmarkReport 对象格式化输出 (Console, File)
    """
    
    @staticmethod
    def print_summary(report: BenchmarkReport):
        """打印控制台摘要报告"""
        print("\n" + "=" * 80)
        print("📊 CRBench 测试报告 (Summary)")
        print("=" * 80)
        
        overall = report.overall_metrics
        print(f"\n📈 整体指标:")
        print(f"   总用例数: {overall['total_cases']}")
        print(f"   通过: {overall['passed']} | 失败: {overall['failed']}")
        print(f"   准确率 (Precision): {overall['precision']:.2%}")
        print(f"   召回率 (Recall):    {overall['recall']:.2%}")
        print(f"   F1分数:             {overall['f1_score']:.2%}")
        print(f"   正确率 (Accuracy):  {overall['accuracy']:.2%}")
        
        print(f"\n📊 按维度统计:")
        for dimension, metrics in report.by_dimension.items():
            print(f"\n   {dimension}:")
            print(f"      P={metrics['precision']:.2%} | R={metrics['recall']:.2%} | F1={metrics['f1_score']:.2%}")
        
        if report.failed_cases:
            print(f"\n❌ 失败用例 ({len(report.failed_cases)}):")
            for case in report.failed_cases[:10]:
                print(f"   - {case['case_id']}: {case['reason']}")
            if len(report.failed_cases) > 10:
                print(f"   ... 等共 {len(report.failed_cases)} 个")
        
        print("\n" + "=" * 80)
    
    @staticmethod
    def save_json(report: BenchmarkReport, output_file: str):
        """保存完整 JSON 报告"""
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(asdict(report), f, indent=2, ensure_ascii=False)
        print(f"\n💾 报告已保存: {output_file}")


def main():
    """CLI 入口"""
    parser = argparse.ArgumentParser(description='CRBench - 代码评审基准测试')
    parser.add_argument('--all', action='store_true', help='运行所有测试用例')
    parser.add_argument('--quick', action='store_true', help='快速模式：只测试5个用例 (用于调试)')
    parser.add_argument('--dimension', type=str, action='append', help='只测试指定维度 (可多次使用)')
    parser.add_argument('--serial', action='store_true', help='强制使用串行模式 (默认并发)')
    parser.add_argument('--output', type=str, help='指定输出JSON报告的文件路径')
    
    args = parser.parse_args()
    
    # 初始化 Runner
    runner = BenchmarkRunner(benchmark_dir=str(BENCHMARK_DIR))
    
    # 路由逻辑
    if args.dimension:
        # 运行指定维度
        all_results = []
        for dim in args.dimension:
            report = runner.run_dimension(dim, parallel=not args.serial)
            all_results.extend(report.all_results)
        # 重新聚合生成总报告
        results = [TestResult(**r) for r in all_results]
        report = runner._generate_report(results)
    else:
        # 运行全部或快速模式
        report = runner.run_all(quick=args.quick, parallel=not args.serial)
    
    # 输出结果
    ReportGenerator.print_summary(report)
    
    # 保存 JSON
    results_dir = BENCHMARK_DIR / "results"
    results_dir.mkdir(exist_ok=True)
    output_file = args.output if args.output else results_dir / f"benchmark_{time.strftime('%Y%m%d_%H%M%S')}.json"
    ReportGenerator.save_json(report, str(output_file))


if __name__ == '__main__':
    main()
