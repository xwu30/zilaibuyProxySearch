#!/bin/bash
set -e

APP_NAME="zilaibuy-backend"
ENV_NAME="Zilaibuy-backend-env"
REGION="us-east-1"
JAR="target/zilaibuy-backend-0.0.1.jar"
VERSION_LABEL="v$(date +%Y%m%d%H%M%S)"

echo "==> Building JAR..."
./mvnw clean package -DskipTests

echo "==> Getting EB S3 bucket..."
S3_BUCKET=$(aws elasticbeanstalk create-storage-location \
  --region "$REGION" \
  --query S3Bucket --output text)

S3_KEY="$APP_NAME/$VERSION_LABEL.jar"

echo "==> Uploading $JAR to s3://$S3_BUCKET/$S3_KEY..."
aws s3 cp "$JAR" "s3://$S3_BUCKET/$S3_KEY" --region "$REGION"

echo "==> Creating application version $VERSION_LABEL..."
aws elasticbeanstalk create-application-version \
  --region "$REGION" \
  --application-name "$APP_NAME" \
  --version-label "$VERSION_LABEL" \
  --source-bundle S3Bucket="$S3_BUCKET",S3Key="$S3_KEY" \
  --no-auto-create-application

echo "==> Deploying to $ENV_NAME..."
aws elasticbeanstalk update-environment \
  --region "$REGION" \
  --environment-name "$ENV_NAME" \
  --version-label "$VERSION_LABEL"

echo "==> Waiting for deployment to complete..."
aws elasticbeanstalk wait environment-updated \
  --region "$REGION" \
  --environment-names "$ENV_NAME"

echo "==> Done! Version $VERSION_LABEL deployed to $ENV_NAME."
