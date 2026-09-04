# DocuForge AI — frontend (Phase 8 Dynamic Forms)
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:1.27.4-alpine
LABEL org.opencontainers.image.title="docuforge-ai-frontend" \
      org.opencontainers.image.description="DocuForge AI React UI — Dynamic Forms"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]