# Scala REST API

Neste projeto é desenvolvida uma API REST utilizando a linguagem de programação Scala. O banco de dados escolhido foi o banco NoSQL MongoDB e o framework de desenvolvimento é o Play. Abaixo seguem todas as versões utilizadas:

<center>

|                 Tecnologia             | Versão      |
|:--------------------------------------:|:-----------:|
|                   Scala                |    2.13.1   |
|                  MongoDB               |    4.2.0    |
|               Play Framework           |    2.4.11   |
</center>

## Endpoints

### POST - ```/api/user/add``` - Adiciona um novo usuário.

Modelo do JSON:
```
{
	"name":"Usuário Teste",
	"cpf":"000000000",
	"rg": "000000000",
	"date_birth": "2019-04-23",
	"mother_name":"Teste mãe",
	"father_name":"Teste pai"
}
```

### POST - ```/api/user/:digit``` - Adiciona uma novo foto de usuário.

> O campo ```:digit``` requer um CPF ou RG de um usuário da base.

Modelo de request body:
```
HEADER: multipart/form-data
BODY: key: picture, value: [CAMINHO DA IMAGEM]
```

### GET - ```/api/users``` - Retorna todos os usuários.

### DELETE - ```/api/user/delete/:digit``` - Excluir um usuário.

> O campo ```:digit``` requer um CPF ou RG de um usuário da base.

### GET - ```/api/user/:digit``` - Retorna um usuário.

> O campo ```:digit``` requer um CPF ou RG de um usuário da base.

### GET - ```/api/users/s/:name``` - Pesquisa pelos usuários da base.

> O campo ```:name``` requer uma string.
Obs: busca case-sensitive.

### GET - ```/api/cleanup``` - Utilitário de exclusão dos registros.

# Notas importantes

## Documentação PostMan

Rotas pelo PostMan

https://documenter.getpostman.com/view/1857442/SVtYTnGX?version=latest#85731ac9-26c8-55d8-cd1e-b8fadeb942c6

## Restrição de unicidade no MongoDB

O MongoDB não aceita duplicação de documentos para "RG" e "CPF" com o comando:

```db.widgets.createIndex( { cpf: 1, rg: 1 }, { unique: true } )```

Resolvendo problemas do Play Framework com Windows para UTF-8. Entre na pasta projeto e insira:

```set JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF8"```

# Referências

https://www.playframework.com/ - Documentação Play Framework <br>
http://reactivemongo.org/ - Documentação Driver MongoDB ReactiveMongo<br>
https://www.mongodb.com/ - Documentação MongoDB