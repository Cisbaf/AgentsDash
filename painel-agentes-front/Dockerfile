# 1️⃣ Build stage
FROM node:20-alpine AS builder

WORKDIR /app

# Copia apenas o necessário primeiro (melhor cache)
COPY package.json package-lock.json* ./

# Instala dependências
RUN npm install

# Copia o restante do projeto
COPY . .

# Build do Next
RUN npm run build


# 2️⃣ Runtime stage (imagem menor)
FROM node:20-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production

# Copia apenas o necessário para rodar
COPY --from=builder /app/package.json ./
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public

EXPOSE 3000

CMD ["npm", "start"]