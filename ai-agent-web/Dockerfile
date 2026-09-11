# ====================================================================
# AI 开放平台前端 - 多阶段构建镜像
# 构建：docker build -t ai-agent-web:latest .
# ====================================================================

# ---------- 构建阶段 ----------
FROM node:20-alpine AS builder
WORKDIR /app

# 先复制依赖清单，利用 Docker 层缓存加速后续构建
COPY package.json package-lock.json ./
# HUSKY=0：容器内无 .git，跳过 husky prepare 脚本避免安装失败
RUN HUSKY=0 npm ci --registry=https://registry.npmmirror.com

# 复制源码并构建（build-only 跳过 vue-tsc 类型检查，避免类型告警阻断镜像构建）
COPY . .
RUN npm run build-only

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine

# 复制构建产物与 nginx 配置
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
