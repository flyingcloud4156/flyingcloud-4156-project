ENV_FILE=.env-flyingcloud
DC=docker compose --env-file $(ENV_FILE) -f docker-compose.yml

.PHONY: up down rebuild logs ps clean app-shell mysql redis
up:
	$(DC) up -d --build

down:
	$(DC) down --remove-orphans

rebuild:
	$(DC) build --no-cache app

logs:
	$(DC) logs -f

ps:
	$(DC) ps

clean: down
	rm -rf ops/mysql/data ops/redis/data
	mkdir -p ops/mysql/{data,conf,init} ops/redis/data
	sudo chown -R 999:999 ops/mysql ops/redis || true

app-shell:
	docker exec -it $$(grep APP_CTN $(ENV_FILE) | cut -d= -f2) /bin/bash || true

mysql:
	docker exec -it $$(grep MYSQL_CTN $(ENV_FILE) | cut -d= -f2) mysql -uroot -p$$(grep DB_ROOT_PASS $(ENV_FILE) | cut -d= -f2)

redis:
	docker exec -it $$(grep REDIS_CTN $(ENV_FILE) | cut -d= -f2) redis-cli -a $$(grep REDIS_PASS $(ENV_FILE) | cut -d= -f2)