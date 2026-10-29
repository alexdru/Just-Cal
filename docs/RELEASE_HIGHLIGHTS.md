# v0.19.4

- Close the active download connection when pausing or cancelling, before waiting for cleanup.
  Evidence: PENDING
- Resume restarted downloads without associating old partial bytes with a new server validator.
  Evidence: PENDING
- Ignore late transfer callbacks after installation to prevent duplicate model downloads.
  Evidence: PENDING
- Select compatible, admitted models immediately after installation, without a preliminary Playground run.
  Evidence: d739ec2987a85a683294969766ac4fe93cf16ec0
- Optionally fill missing package protein, fat and carbohydrate values with local Gemma assistance.
  Evidence: 83051cf520660135a540c3ca5cd77c45fd52dcde
