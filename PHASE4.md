# APIs

## Modify `/{shortURL}` API

* if value not found in cache then 
* check if `shortURL` has prefix '_' then proceed with existing flow
* else if now prefix '_' check `shortURL` in `custome_url_code` table, if not exists respond the n 404 response.]
* bring record from short_url if expiry greater that current timestamp or is null

## Shorten API (Modify)

* **Method** : POST
* **PATH** : /shorten
* **Authorization** : bearer <token>
* **RequestBody** : ``{ url: <string>, short_url: <string:optional>, expiry: <timestamp with zone> }``
* **ResponseBody** : ``{ short_url: <string> }``

### token verification logic
1. split token by '.'
2. perform AES decrypt of first portion as `user_id`
3. hash get from redis for key `token:<user_id>`, field_name as `<seconf portion of split>` 
4. if the field_name exists token is valid and can proceed with `userId` set to request context.

### Logic
1. Validate if ``body.url`` holds a valid URL expression
2. if ``body.short_url`` present, then check if exists in `custome_url_code` table. if exists respond duplicate error
3. Create ``long_url_hash`` from ``body.url`` using ``SHA256`` algorithm
4. Get `userId` from request context.
5. Use `body.expiry` to set as expiry in table
6. Insert the record into table if ``long_url_hash`` and ``long_url`` not exists
7. Insert into ``body.short_url`` into custome_url_code
8. Create a Base62 encoding of the number and add prefix '_'
9. Return if ``body.short_url`` present ``response.body.short_url={host}/{body.short_url}`` else ``response.body.short_url={host}/{encoded_string}`` 

# DB Schema

## alter short_url
| column_name    | type | Indexing |
| -------- | ------- | ----------- |
| expiry  | timesampz (nullable)   | |

## custome_url_code
| column_name    | type | Indexing |
| -------- | ------- | ----------- |
| code  | varchar(100)   | primary key |
| url_id | big int | forign key short_url.id |

# Local Run Setup

## Containarise the applicaiton in java-21 alpine 

* create build step of jar in one stage
* use the build jar to create container in next stage

## Compose

* create postgress with env set for username, password and db_name
* create redis 
* build app with all configuration envs

## Script

* prompt for credentials
* set them as env
* run `docker compose up --build`


