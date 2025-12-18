# DB Schema

## user
| column_name    | type | Indexing |
| -------- | ------- | ----------- |
| id  | Big Int (auto)   | primary key |
| username | varchar(100)     | unique contrain index|
| password_hash    | varchar(255)    |  |
| created_at    | timestampz    | |
| updated_at    | timestampz    | |

# API

## 1. Create User

* **Method** : POST
* **PATH** : /user
* **RequestBody** : 
    ```
    { 
        "username": <string>,
        "password": <string>
    }
    ```

### Logic
1. Validate if ``body.username`` is alpha numeric
2. Check if username already exists in table
2. Create password hash with bcrypt
3. Insert the record into table if ``username`` not exists
5. Respond 
    ```json
    {
        "code": "SUCCESS",
        "message": "User created successfully."
    }
    ```

## Shorten API (Modify)

* **Method** : POST
* **PATH** : /shorten
* **Authorization** : basic
* **RequestBody** : ``{ url: <string> }``
* **ResponseBody** : ``{ short_url: <string> }``

### Logic
1. Validate if ``body.url`` holds a valid URL expression
2. Create ``long_url_hash`` from ``body.url`` using ``SHA256`` algorithm
3. Insert the record into table if ``long_url_hash`` and ``long_url`` not exists
4. Create a Base62 encoding of the number
5. Return ``response.body.short_url={host}/{encoded_string}``

## Error Responses

### Unauthorized

**HTTP STATUS**: 401
```json
{
    "code": "UNAUTHORIZED",
    "message": "invliad user credentials"
}
```

# Rate Limiting

* Use Spring Date Reactive Redis
* implement sliding window counter
* implement rate limit for ``/{shortURL}`` API based on ``shortURL`` params value
    * pick window from property ``rate_limit.shorten.get.size``
    * pick capacity from property ``rate_limit.shorten.get.capacity``
* implement rate limit for ``/shorten`` API based on ``username``
    * start window on first successfull creation
    * pick window from property ``rate_limit.shorten.post.size``
    * pick capacity from property ``rate_limit.shorten.post.capacity``

