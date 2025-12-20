# Caching

## `/{shortURL}` API

modify logic as follow

* instead of directly querying db first check if redis has key `short:<short_url>` if present then value would be long url
* if not present then fetch from db update redis with the long url in key `short:<short_url>`
* redirect to the long_url.
* make use of rlock to avoid multiple request quering db for the same record.
* only one request should from multiple requests for the same short_url gets data from db others wait until redis gets updated.

# APIs

## LogIn API

* **Method** : POST
* **PATH** : /user/login
* **RequestBody** : 
  ```
  {
    "username": string,
    "password": string
  }
  ```
* **ResponseBody** : ``{ token: <string> }``

### Logic
1. Validate if ``body.username`` is alpha numeric
2. Get record with username from user table
3. Create password hash with bcrypt and check if match with `password_hash` from db
4. Encrypt `user.id` using AES 256
5. Generate a random crypto string of length as configured
6. insert record in redis as hash set with key as `token:<user.id>`, field as `<random_string>` with value true and ttl for the field as per configured
7. return token `<encrypted(user.id)>.<random_string>`

## Shorten API (Modify)

* **Method** : POST
* **PATH** : /shorten
* **Authorization** : bearer <token>
* **RequestBody** : ``{ url: <string> }``
* **ResponseBody** : ``{ short_url: <string> }``

### token verification logic
1. split token by '.'
2. perform AES decrypt of first portion as `user_id`
3. hash get from redis for key `token:<user_id>`, field_name as `<seconf portion of split>` 
4. if the field_name exists token is valid and can proceed with `userId` set to request context.

### Logic
1. Validate if ``body.url`` holds a valid URL expression
2. Create ``long_url_hash`` from ``body.url`` using ``SHA256`` algorithm
3. Get `userId` from request context.
4. Insert the record into table if ``long_url_hash`` and ``long_url`` not exists
5. Create a Base62 encoding of the number
6. Return ``response.body.short_url={host}/{encoded_string}``

# DB Schema

## alter short_url
| column_name    | type | Indexing |
| -------- | ------- | ----------- |
| owner  | Big Int (auto)   | forign key user.id |