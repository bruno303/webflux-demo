### Curl requests

```shell
$ curl -i -X POST http://localhost:8080/person/random
```

```shell
$ curl -i -X POST http://localhost:8080/person/random2
```

```shell
$ curl -i -X POST -H "Idempotency-Id: {{value here}}" http://localhost:8080/person/random/idempotency
```