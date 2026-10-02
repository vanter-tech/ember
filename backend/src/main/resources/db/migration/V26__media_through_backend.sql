-- Menu and category photos move from direct Google Cloud Storage URLs to the backend's
-- /public/media route (the bucket is closed afterwards). Only rows still pointing at GCS are
-- rewritten, keeping the object name (<uuid>.jpg); dev and Hub rows are untouched.
-- ${mediaPublicUrl} is MINIO_PUBLIC_URL (spring.flyway.placeholders).
UPDATE categories
SET img_url = '${mediaPublicUrl}/' || regexp_replace(img_url, '^.*/', '')
WHERE img_url LIKE 'https://storage.googleapis.com/%'
  AND img_url NOT LIKE '${mediaPublicUrl}/%';

UPDATE menu_items
SET image_url = '${mediaPublicUrl}/' || regexp_replace(image_url, '^.*/', '')
WHERE image_url LIKE 'https://storage.googleapis.com/%'
  AND image_url NOT LIKE '${mediaPublicUrl}/%';
