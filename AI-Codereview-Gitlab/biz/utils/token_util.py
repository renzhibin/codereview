import tiktoken
from typing import Optional


def _safe_get_encoding(encoding_name: str = "cl100k_base") -> Optional["tiktoken.Encoding"]:
    """
    在离线 / 受限网络环境下安全获取 tiktoken 编码器。

    正常情况返回 Encoding；如果拉取 BPE 词表失败（需要联网被墙等），返回 None，
    由调用方决定降级策略，避免因为网络问题把整体功能跑挂。
    """
    try:
        return tiktoken.get_encoding(encoding_name)
    except Exception:
        return None


def count_tokens(text: str) -> int:
    """
    计算文本的 token 数量。

    优先使用 tiktoken；如果当前环境无法加载词表，则退化为基于字符长度的近似估算，
    保证在离线环境下依然可用（只是截断不那么精确）。
    """
    encoding = _safe_get_encoding("cl100k_base")
    if encoding is not None:
        return len(encoding.encode(text))

    # 退化策略：大致按 2 个字符 ≈ 1 个 token 估算
    return max(1, len(text) // 2)


def truncate_text_by_tokens(text: str, max_tokens: int, encoding_name: str = "cl100k_base") -> str:
    """
    根据最大 token 数量截断文本。

    优先用 tiktoken 精确截断；如果获取编码器失败，则退化为按字符长度截断，
    近似认为 2 个字符 ≈ 1 个 token，宁可多给一点上下文，也不要直接抛错。
    """
    encoding = _safe_get_encoding(encoding_name)
    if encoding is not None:
        tokens = encoding.encode(text)
        if len(tokens) > max_tokens:
            truncated_tokens = tokens[:max_tokens]
            return encoding.decode(truncated_tokens)
        return text

    approx_max_chars = max_tokens * 2
    if len(text) > approx_max_chars:
        return text[:approx_max_chars]
    return text


if __name__ == "__main__":
    sample = "Hello, world! This is a test text for token counting."
    print(count_tokens(sample))
    print(truncate_text_by_tokens(sample, 5))


