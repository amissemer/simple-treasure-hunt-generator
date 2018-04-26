A simple generator for treasure hunt puzzle pages
================

Prerequisites
--------

Either `groovy` or `Docker` must be installed.

On Mac, you can install `groovy` as follow:

1. install [Brew](https://brew.sh/)

```
/usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
```

2. install `groovy`

```
brew install groovy
```

Usage
-----

Put your questions, answers and follow-up text in a `.yaml` file in the `games` directory. Look at `games/demo.yaml` for example. Each `games/*.yaml` file will be used to generate a set of static html pages from the templates, and will be uploaded to Amazon S3 under a separate folder, so you can keep the existing `yaml` files and work on several game files at the same time if you want. You can also put all puzzles for all teams in the same file if you want, as long as the puzzle keys are unique.

Copy the `secret.properties.template` to `secret.properties` and provide AWS credentials with write permission to the configured AWS bucket.

Then run with one of the following:

1) On Mac OS X, if you have groovy installed

```
make
```

2) On Windows, with Groovy installed you can run:

```
groovy generate
```

3) With Docker (Docker for Mac or Docker for Windows), and without having to install Groovy:

```
docker-compose run --rm groovy
```

4) Or with Docker but using make, a bit simpler to remember

```
make docker-build
```



Required Amazon S3 configuration
-------------

- A single S3 bucket is required and should be configured in the `secret.properties`.
- An Amazon IAM user with an access key ID and secret key, also configured in `secret.properties`.
- The bucket **MUST NOT** allow Public access to List objects
- The bucket **MUST** allow `s3:GetObject` on all the bucket objects, which can be done with the following policy (adapt the name of the Resource with your bucket name)

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "VisualEditor0",
            "Effect": "Allow",
            "Principal": "*",
            "Action": "s3:GetObject",
            "Resource": "arn:aws:s3:::es-treasure-hunt/*"
        }
    ]
}
```

- The IAM user **MUST** have read/write access to the bucket content. More specifically the following actions are required in a policy attached to the user:
   - ListAllMyBuckets
   - ListBucket
   - GetBucketLocation
   - GetObject
   - PutObject
   - DeleteObject

You can adapt from the following policy:

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "VisualEditor0",
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:ListBucket",
                "s3:DeleteObject",
                "s3:GetBucketLocation"
            ],
            "Resource": [
                "arn:aws:s3:::es-treasure-hunt",
                "arn:aws:s3:::es-treasure-hunt/*"
            ]
        },
        {
            "Sid": "VisualEditor1",
            "Effect": "Allow",
            "Action": "s3:ListAllMyBuckets",
            "Resource": "*"
        }
    ]
}
```