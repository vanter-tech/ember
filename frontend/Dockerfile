FROM node:20-alpine AS BUILD
RUN npm install -g pnpm
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY entrypoint.sh /docker-entrypoint.d/99-env-injector.sh
RUN chmod +x /docker-entrypoint.d/99-env-injector.sh

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]