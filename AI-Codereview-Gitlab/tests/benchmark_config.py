"""
代码评审基准测试配置

所有测试代码和测试用例都在 tests/ 目录下
提示词使用 conf/review_prompt_eval.md（Markdown格式，无需pyyaml）
"""

# ============================================================================
# LLM 配置
# ============================================================================

LLM_API_KEY = "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"
LLM_API_BASE = "https://gateway.ai.cloudflare.com/v1/99fb7df59e08bdb8bde2ba7708d9f96e/kc/openrouter/v1/"
REVIEW_MODEL = "qwen/qwen3-coder-30b-a3b-instruct"
RECHECK_MODEL = "qwen/qwen3-32b"

# ============================================================================
# 测试配置
# ============================================================================

# 并发配置
MAX_WORKERS = 10  # 最大并发数（可根据机器性能调整）

# 环境变量
ENV_CONFIG = {
    "LLM_PROVIDER": "openai",
    "OPENAI_API_KEY": LLM_API_KEY,
    "OPENAI_API_BASE_URL": LLM_API_BASE,
    "OPENAI_API_MODEL": REVIEW_MODEL,
    "CONTEXT_ENABLED": "0",  # 禁用自动上下文获取
}

def get_env_config():
    """获取环境变量配置"""
    return ENV_CONFIG.copy()
