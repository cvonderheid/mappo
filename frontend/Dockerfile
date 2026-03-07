FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . ./
RUN npm run build

FROM nginx:1.27-alpine

COPY nginx/mappo-frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker-entrypoint.d/40-mappo-runtime-config.sh /docker-entrypoint.d/40-mappo-runtime-config.sh

RUN chmod +x /docker-entrypoint.d/40-mappo-runtime-config.sh

EXPOSE 80
