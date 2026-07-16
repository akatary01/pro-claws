# Nayax SQS Transaction Delivery

This stack creates the AWS pieces needed for Nayax real-time transaction delivery into Vendistri:

- Standard SQS queue for Nayax transaction messages
- Standard SQS dead-letter queue
- Limited IAM user/access key for Nayax to send to that queue
- Lambda relay that reads SQS and posts each Nayax transaction JSON to Vendistri

This stack is intended to be deployed once per Nayax telemetry connection while the integration is new. That keeps credentials and routing isolated.

## Before Deploying

Create or identify the Vendistri Nayax telemetry provider row for the customer/org.

You need:

- `VendistriTransportUsername`: Vendistri telemetry provider ID
- `VendistriTransportPassword`: transport secret/API key for that provider
- `VendistriEndpoint`: usually `https://secure.vendistri.com/vendistri/be/telemetry/transport/nayax/sqs`

## Deploy In AWS Console

1. Log in to the Vendistri AWS account.
2. Open CloudFormation.
3. Choose **Create stack**.
4. Choose **Upload a template file**.
5. Upload `infra/nayax-sqs/template.yaml`.
6. Stack name example: `vendistri-nayax-sqs-customer-name`.
7. Fill parameters:
   - `QueueName`: unique queue name, for example `vendistri-nayax-transactions-customer-name`
   - `VendistriEndpoint`
   - `VendistriTransportUsername`
   - `VendistriTransportPassword`
8. Acknowledge IAM resource creation.
9. Create stack.

## Values To Give Nayax

After the stack completes, open the stack outputs and copy:

- `NayaxQueueUrl`
- `NayaxAccessKeyId`
- `NayaxSecretAccessKey`
- `AwsRegion`

Store the same values on the Vendistri Nayax `sqs_push` telemetry method using these config keys:

- `public_config.queueUrl`: `NayaxQueueUrl`
- `public_config.accessKeyId`: `NayaxAccessKeyId`
- `public_config.region`: `AwsRegion`
- `private_config.secretAccessKey`: `NayaxSecretAccessKey`

Give these to Nayax Core in:

```text
Administration -> Operator -> Transactions Report -> Amazon SQS
```

Required Nayax roles:

- Transaction Dispatcher
- Transactions Report Subscriber

Select product/transaction fields, including:

- TransactionId
- MachineId
- Settlement Time
- Payment Method Description
- SeValue / Payed Value
- Products
- Product Name
- Product Quantity
- Product Bruto
- Product Catalog Number
- Product ID
- Product PA Code
- Product Code in Map

## How It Works

```text
Nayax Core
  -> SQS queue
  -> Lambda relay
  -> Vendistri /telemetry/transport/nayax/sqs
  -> MachineSale / MachineSaleLine / InventoryMovement
```

The Lambda sends one Vendistri request per SQS message and reports per-message failures to SQS. Failed messages retry and eventually move to the DLQ.

## Security Notes

- The Nayax IAM user can only send messages and read attributes for its queue.
- Do not reuse the same access key across customers.
- Store `NayaxSecretAccessKey` securely; CloudFormation will expose it in outputs for setup.
- Rotate credentials if a customer disconnects Nayax or if a secret is exposed.
