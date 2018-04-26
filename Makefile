all: build
.PHONY : all

build:
	groovy generate

docker-build:
	docker-compose run --rm groovy