import json
import os
import re
import subprocess
import time
from pathlib import Path
from urllib.parse import urlparse
from typing import Optional

from biz.utils.log import logger


class GitManager:
    """Git仓库管理器，支持浅克隆和缓存策略"""
    
    def __init__(self):
        self.cache_dir = os.getenv('REPOS_CACHE_DIR', 'data/repos')
        self.cache_metadata_file = os.path.join(os.path.dirname(self.cache_dir), 'repos_cache.json')
        self.expire_days = int(os.getenv('REPOS_CACHE_EXPIRE_DAYS', '30'))
        self._ensure_cache_dir()
    
    def _ensure_cache_dir(self):
        """确保缓存目录存在"""
        os.makedirs(self.cache_dir, exist_ok=True)
        if not os.path.exists(self.cache_metadata_file):
            self._save_cache_metadata({})
    
    def ensure_repo(self, repo_url: str, branch: str, project_name: str, access_token: str) -> str:
        """
        确保本地有指定分支的仓库，使用缓存策略
        
        Args:
            repo_url: 仓库URL
            branch: 分支名
            project_name: 项目名称
            access_token: 访问令牌
        
        Returns:
            本地仓库路径
        """
        try:
            project_slug = self._slugify(project_name)
            repo_path = self._get_cache_path(project_slug, branch)
            
            if os.path.exists(repo_path) and os.path.exists(os.path.join(repo_path, '.git')):
                logger.info(f"仓库缓存已存在: {repo_path}, 执行增量更新")
                try:
                    self._update_repo(repo_path, branch)
                except Exception as e:
                    logger.warning(f"增量更新失败: {e}, 删除旧缓存重新克隆")
                    self._remove_repo(repo_path)
                    self._clone_repo(repo_url, branch, repo_path, access_token)
            else:
                logger.info(f"仓库缓存不存在，执行浅克隆: {repo_path}")
                self._clone_repo(repo_url, branch, repo_path, access_token)
            
            # 更新缓存元数据
            self._update_cache_metadata(project_slug, branch)
            
            return repo_path
        except Exception as e:
            logger.error(f"确保仓库失败: {e}")
            raise
    
    def _clone_repo(self, repo_url: str, branch: str, repo_path: str, access_token: str):
        """
        浅克隆仓库
        
        Args:
            repo_url: 仓库URL
            branch: 分支名
            repo_path: 本地路径
            access_token: 访问令牌
        """
        try:
            # 确保父目录存在
            os.makedirs(os.path.dirname(repo_path), exist_ok=True)
            
            # 在URL中注入access_token
            auth_url = self._inject_token(repo_url, access_token)
            
            cmd = [
                'git', 'clone',
                '--depth=1',                    # 只克隆最新提交
                '--single-branch',              # 只克隆单个分支
                '--branch', branch,
                auth_url,
                repo_path
            ]
            
            logger.info(f"执行git clone命令: git clone --depth=1 --single-branch --branch {branch} <repo_url> {repo_path}")
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            
            if result.returncode != 0:
                raise Exception(f"Git clone失败: {result.stderr}")
            
            logger.info(f"Git clone成功: {repo_path}")
        except subprocess.TimeoutExpired:
            logger.error("Git clone超时（300秒）")
            raise Exception("Git clone超时")
        except Exception as e:
            logger.error(f"Git clone失败: {e}")
            # 清理失败的克隆
            if os.path.exists(repo_path):
                self._remove_repo(repo_path)
            raise
    
    def _update_repo(self, repo_path: str, branch: str):
        """
        增量更新已存在的仓库
        
        Args:
            repo_path: 仓库路径
            branch: 分支名
        """
        try:
            # 先fetch
            logger.info(f"执行git fetch: {repo_path}")
            result = subprocess.run(
                ['git', 'fetch', 'origin', branch],
                cwd=repo_path,
                capture_output=True,
                text=True,
                timeout=120
            )
            
            if result.returncode != 0:
                raise Exception(f"Git fetch失败: {result.stderr}")
            
            # 再reset
            logger.info(f"执行git reset --hard: {repo_path}")
            result = subprocess.run(
                ['git', 'reset', '--hard', f'origin/{branch}'],
                cwd=repo_path,
                capture_output=True,
                text=True,
                timeout=30
            )
            
            if result.returncode != 0:
                raise Exception(f"Git reset失败: {result.stderr}")
            
            logger.info(f"Git更新成功: {repo_path}")
        except subprocess.TimeoutExpired:
            logger.error("Git更新超时")
            raise Exception("Git更新超时")
        except Exception as e:
            logger.error(f"Git更新失败: {e}")
            raise
    
    def _inject_token(self, repo_url: str, access_token: str) -> str:
        """
        在Git URL中注入access token
        
        Args:
            repo_url: 原始URL
            access_token: 访问令牌
        
        Returns:
            带认证的URL
        """
        try:
            # 移除URL中已有的认证信息
            repo_url = re.sub(r'https?://[^@]+@', 'https://', repo_url)

            parsed = urlparse(repo_url)

            # 如果 GitLab 跑在 Docker 里，项目 webhook 里的 host 可能是容器名（如 f1f5b7ff8e85），
            # 在宿主机上直接访问会解析失败。
            # 这里如果发现 GITLAB_URL 被配置了，就用它的 host 覆盖掉 repo_url 里的 host。
            gitlab_url = os.getenv("GITLAB_URL")
            if gitlab_url:
                try:
                    g_parsed = urlparse(gitlab_url)
                    if g_parsed.scheme and g_parsed.netloc:
                        parsed = parsed._replace(netloc=g_parsed.netloc)
                except Exception:
                    # 配置异常就忽略，继续使用原始 host
                    pass

            # 根据平台选择认证方式
            # 对于 HTTP GitLab，我们统一使用 oauth2:{token} 形式，避免交互式密码输入。
            gitlab_host = None
            if gitlab_url:
                try:
                    g_parsed = urlparse(gitlab_url)
                    gitlab_host = g_parsed.netloc
                except Exception:
                    gitlab_host = None

            if gitlab_host and parsed.netloc == gitlab_host:
                # 当前 host 与 GITLAB_URL 一致，按 GitLab 处理
                auth = f'oauth2:{access_token}'
            elif 'gitlab' in parsed.netloc.lower():
                auth = f'oauth2:{access_token}'
            else:
                # 其他平台（如 GitHub）
                auth = access_token

            # 重新组装URL
            auth_url = f"{parsed.scheme}://{auth}@{parsed.netloc}{parsed.path}"

            return auth_url
        except Exception as e:
            logger.error(f"注入token失败: {e}")
            raise
    
    def _get_cache_path(self, project_slug: str, branch: str) -> str:
        """
        获取缓存路径
        
        Args:
            project_slug: 项目slug
            branch: 分支名
        
        Returns:
            缓存路径
        """
        branch_slug = self._slugify(branch)
        return os.path.join(self.cache_dir, project_slug, branch_slug)
    
    def _slugify(self, text: str) -> str:
        """
        将文本转换为适合作为文件名的字符串
        
        Args:
            text: 原始文本
        
        Returns:
            slug化的文本
        """
        # 移除URL scheme
        text = re.sub(r'^https?://', '', text)
        # 替换非字母数字字符为下划线
        text = re.sub(r'[^a-zA-Z0-9]', '_', text)
        # 移除尾部下划线
        text = text.rstrip('_')
        return text
    
    def _remove_repo(self, repo_path: str):
        """
        删除仓库目录
        
        Args:
            repo_path: 仓库路径
        """
        try:
            import shutil
            if os.path.exists(repo_path):
                shutil.rmtree(repo_path)
                logger.info(f"已删除仓库: {repo_path}")
        except Exception as e:
            logger.error(f"删除仓库失败: {e}")
    
    def _update_cache_metadata(self, project_slug: str, branch: str):
        """
        更新缓存元数据
        
        Args:
            project_slug: 项目slug
            branch: 分支名
        """
        try:
            metadata = self._load_cache_metadata()
            key = f"{project_slug}/{branch}"
            metadata[key] = {
                'last_access_time': int(time.time()),
                'project_slug': project_slug,
                'branch': branch
            }
            self._save_cache_metadata(metadata)
        except Exception as e:
            logger.error(f"更新缓存元数据失败: {e}")
    
    def _load_cache_metadata(self) -> dict:
        """
        加载缓存元数据
        
        Returns:
            元数据字典
        """
        try:
            if os.path.exists(self.cache_metadata_file):
                with open(self.cache_metadata_file, 'r', encoding='utf-8') as f:
                    return json.load(f)
            return {}
        except Exception as e:
            logger.error(f"加载缓存元数据失败: {e}")
            return {}
    
    def _save_cache_metadata(self, metadata: dict):
        """
        保存缓存元数据
        
        Args:
            metadata: 元数据字典
        """
        try:
            with open(self.cache_metadata_file, 'w', encoding='utf-8') as f:
                json.dump(metadata, f, indent=2, ensure_ascii=False)
        except Exception as e:
            logger.error(f"保存缓存元数据失败: {e}")
    
    def clean_expired_cache(self):
        """
        清理过期的缓存（可选功能）
        """
        try:
            metadata = self._load_cache_metadata()
            current_time = int(time.time())
            expire_threshold = self.expire_days * 24 * 3600
            
            expired_keys = []
            for key, info in metadata.items():
                last_access = info.get('last_access_time', 0)
                if current_time - last_access > expire_threshold:
                    expired_keys.append(key)
                    # 删除仓库目录
                    repo_path = self._get_cache_path(info['project_slug'], info['branch'])
                    self._remove_repo(repo_path)
            
            # 从元数据中移除
            for key in expired_keys:
                del metadata[key]
            
            self._save_cache_metadata(metadata)
            
            if expired_keys:
                logger.info(f"清理了 {len(expired_keys)} 个过期缓存")
        except Exception as e:
            logger.error(f"清理过期缓存失败: {e}")

