# PHASE 1 Query

```
@PHASE1.MD 
* crearte entity for db schema
* create utitlity for base62 with encode and decode methods
* implement the APIs
* have request response models maintained seperately
* ensure to use only the provided error responses.
```
```
use lombok
```

# Phase 2

```
@PHASE2.md 
* crearte entity for db schema
* implement or update the APIs
* have request response models maintained seperately
* ensure to use only the provided error responses in the doc and the once already implemented in the app.
```

```
@PHASE2.md 
* implement rate limitng
* check has to be made as a prefilter for api calls
```

```
create an openapi doc endpoint
```

# Phase 3

```
@PHASE3.md implement caching for `/{shortURL}` API
```

```
@PHASE3.md implement login api
```

```
@PHASE3.md modify authentication mechanism from basic to bearer. Follow the token verification logic
```

```
@src/main/java/com/example/tinyurl/repository/UserRepository.java write following test cases
1. create a user record and save, then make query `existsByUsername` for the user name and check if true. make a call again with different username to check if false.
2. 2 records with same username should not be possible
3. create a user and save and check if createAt and updatedAt has a valid value
```

```
@src/main/java/com/example/tinyurl/repository/ShortUrlRepository.java create test case for following scenarios
1. create a @src/main/java/com/example/tinyurl/entity/User.java and save then create a @src/main/java/com/example/tinyurl/entity/ShortUrl.java record and save. Verify created at and updatedat has valid values
2. create a @src/main/java/com/example/tinyurl/entity/ShortUrl.java with invalid user id should get exception
3. create multiple @src/main/java/com/example/tinyurl/entity/ShortUrl.java records with valid user and has long_url hash same but different valies. check if no exception happens and both values are present in db on get
```

```
@src/main/java/com/example/tinyurl/service/RateLimitService.java write test case for following scenarios:
1. invoke repeatedly with a gam of 3 seconds `checkGetRateLimit` with a shortUrl and should return true for the first 10 invcation then it shoul return false. again from 21 st invocation it shold return true.
2.  invoke repeatedly with a gam of 5 seconds `checkPostRateLimit` with a shortUrl and should return true for the first 5 invcation then it shoul return false. again from 11 th invocation it shold return true.
```