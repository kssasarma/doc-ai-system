FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
ARG VITE_BACKEND_URL=http://localhost:8080
ARG VITE_INGESTOR_URL=http://localhost:8081
ENV VITE_BACKEND_URL=$VITE_BACKEND_URL
ENV VITE_INGESTOR_URL=$VITE_INGESTOR_URL
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
