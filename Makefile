.DEFAULT_GOAL := help
.PHONY: help up down logs ps build run test compile format clean db-reset

# Command Docker Compose (hỗ trợ cả 'docker compose' lẫn 'docker-compose')
DOCKER_COMPOSE ?= $(shell if command -v docker compose > /dev/null 2>&1; then echo "docker compose"; else echo "docker-compose"; fi)

## help: Hiển thị danh sách các lệnh make khả dụng và hướng dẫn sử dụng
help:
	@echo "==================================================================================="
	@echo "=                   KTX HUB / PDMS - MAKEFILE CLI COMMANDS                        ="
	@echo "==================================================================================="
	@echo "=                                                                                 ="
	@echo "=Hạ tầng & Docker (Local Infrastructure):                                         ="
	@echo "=  make up          Khởi động PostgreSQL 17 và Redis 8 (chạy ngầm -d)             ="
	@echo "=  make down        Dừng các container hạ tầng                                    =" 
	@echo "=  make logs        Xem log thời gian thực của PostgreSQL và Redis                ="
	@echo "=  make ps          Xem trạng thái các container đang chạy                        ="
	@echo "=  make db-reset    Xóa toàn bộ volume database/Redis và khởi tạo lại mới         ="
	@echo "=                                                                                 ="
	@echo "=Phát triển & Build (Application & Testing):                                      ="
	@echo "=  make compile     Biên dịch mã nguồn Java (clean compile)                       ="
	@echo "=  make test        Chạy toàn bộ Unit & Integration tests                         ="
	@echo "=  make build       Đóng gói file JAR (bỏ qua tests)                              ="
	@echo "=  make run         Chạy ứng dụng Spring Boot local với profile dev               ="
	@echo "=  make format      Tự động format code theo chuẩn Google Java Format (Spotless)  ="
	@echo "=  make clean       Xóa thư mục target/ và các file build tạm                     ="
	@echo "=                                                                                 ="
	@echo "==================================================================================="

# Khởi động PostgreSQL và Redis
up:
	$(DOCKER_COMPOSE) -f docker/docker-compose.yml --env-file .env up -d

# Tắt các container
down:
	$(DOCKER_COMPOSE) -f docker/docker-compose.yml --env-file .env down

# Xem logs của các container
logs:
	$(DOCKER_COMPOSE) -f docker/docker-compose.yml --env-file .env logs -f

# Xem trạng thái các container
ps:
	$(DOCKER_COMPOSE) -f docker/docker-compose.yml --env-file .env ps

# Format code chuẩn Google Java Format (thông qua Spotless plugin)
format:
	./mvnw spotless:apply

# Biên dịch mã nguồn Java
compile:
	./mvnw clean compile

# Chạy toàn bộ Unit & Integration tests
test:
	./mvnw test

# Build project (bỏ qua unit tests)
build:
	./mvnw clean package -DskipTests

# Chạy ứng dụng Spring Boot local với profile dev
run:
	./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Xóa target folder
clean:
	./mvnw clean

# Reset toàn bộ database và Redis (xóa volume và khởi động lại container mới tinh)
db-reset:
	$(DOCKER_COMPOSE) -f docker/docker-compose.yml --env-file .env down -v
	$(DOCKER_COMPOSE) -f docker/docker-compose.yml --env-file .env up -d
