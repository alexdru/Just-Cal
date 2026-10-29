# v0.19.4

- Close the active download connection when pausing or cancelling, before waiting for cleanup.
  Evidence: d5597e0a201c47bda8eea6e1bae980dc3f5fdd93
- Resume restarted downloads without associating old partial bytes with a new server validator.
  Evidence: d5597e0a201c47bda8eea6e1bae980dc3f5fdd93
- Ignore late transfer callbacks after installation to prevent duplicate model downloads.
  Evidence: d5597e0a201c47bda8eea6e1bae980dc3f5fdd93
