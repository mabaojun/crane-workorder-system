#!/bin/bash
# 本地开发环境变量：优先 ~/crane-tools（免污染安装），已装系统工具则直接可用
if [ -f "$HOME/crane-tools/env.sh" ]; then
  source "$HOME/crane-tools/env.sh"
fi
export PATH="$HOME/crane-tools/bin:$PATH"
