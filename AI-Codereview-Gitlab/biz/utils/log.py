import logging
import os
import threading
from logging.handlers import RotatingFileHandler
from typing import Optional

# ========== TraceID 相关功能 ==========
# 使用 threading.local 存储 traceid（每个线程独立）
_trace_local = threading.local()


def generate_trace_id_from_mr(webhook_data: dict) -> str:
    """
    从 GitLab Merge Request webhook_data 生成 traceid
    格式: MR-{项目名}-{IID}
    例如: MR-myproject-123
    
    Args:
        webhook_data: GitLab merge_request webhook 数据
        
    Returns:
        traceid 字符串，格式: MR-{project_name}-{iid}
    """
    obj_attrs = webhook_data.get('object_attributes', {})
    iid = obj_attrs.get('iid', '')
    project_name = webhook_data.get('project', {}).get('name', '')
    
    if project_name and iid:
        # 统一使用项目名 + IID（项目名限制长度，避免特殊字符）
        safe_name = project_name.replace(' ', '_').replace('/', '_')[:30]
        return f"MR-{safe_name}-{iid}"
    
    # 降级：使用时间戳
    import time
    return f"MR-{int(time.time())}"


def set_trace_id(trace_id: str) -> None:
    """设置当前线程的 traceid"""
    _trace_local.trace_id = trace_id


def get_trace_id() -> Optional[str]:
    """获取当前线程的 traceid"""
    return getattr(_trace_local, 'trace_id', None)


# ========== 日志相关功能 ==========
# 自定义 Formatter，支持 traceid
class TraceIDFormatter(logging.Formatter):
    """自定义日志格式化器，自动添加 traceid"""
    
    def format(self, record):
        # 从 threading.local 获取 traceid
        trace_id = get_trace_id()
        record.trace_id = trace_id or "N/A"
        return super().format(record)


# 自定义 Logger 类，重写 warn 和 error 方法
class CustomLogger(logging.Logger):
    def warn(self, msg, *args, **kwargs):
        # 在 warn 消息前添加 ⚠️
        msg_with_emoji = f"⚠️ {msg}"
        super().warning(msg_with_emoji, *args, **kwargs)  # 注意：warn 是 warning 的别名

    def error(self, msg, *args, **kwargs):
        # 在 error 消息前添加 ❌
        msg_with_emoji = f"❌ {msg}"
        super().error(msg_with_emoji, *args, **kwargs)


log_file = os.environ.get("LOG_FILE", "log/app.log")
log_max_bytes = int(os.environ.get("LOG_MAX_BYTES", 10 * 1024 * 1024))  # 默认10MB
log_backup_count = int(os.environ.get("LOG_BACKUP_COUNT", 5))  # 默认保留5个备份文件
# 设置日志级别
log_level = os.environ.get("LOG_LEVEL", "INFO")
LOG_LEVEL = getattr(logging, log_level.upper(), logging.INFO)

file_handler = RotatingFileHandler(
    filename=log_file,
    mode='a',
    maxBytes=log_max_bytes,
    backupCount=log_backup_count,
    encoding='utf-8',
    delay=False
)
file_handler.setFormatter(TraceIDFormatter('%(asctime)s - [%(trace_id)s] - %(levelname)s - %(filename)s:%(funcName)s:%(lineno)d - %(message)s'))
file_handler.setLevel(LOG_LEVEL)

console_handler = logging.StreamHandler()
console_handler.setFormatter(TraceIDFormatter('%(asctime)s - [%(trace_id)s] - %(levelname)s - %(filename)s:%(funcName)s:%(lineno)d - %(message)s'))
console_handler.setLevel(LOG_LEVEL)


# 使用自定义的 Logger 类
logger = CustomLogger(__name__)
logger.setLevel(LOG_LEVEL)  # 设置 Logger 的日志级别
logger.addHandler(file_handler)
logger.addHandler(console_handler)
