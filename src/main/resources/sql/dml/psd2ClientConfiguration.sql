[SQL_SELECT_ID_PSD2_CLIENT_CONFIGURATION]
SELECT id FROM psd2ClientConfiguration WHERE id = ?;
;

[SQL_SELECT_ALL_PSD2_CLIENT_CONFIGURATIONS]
SELECT id, clientMode, applicationId, privateKeyPkcs8, callbackUrl, callbackPrivateKeyPkcs8,
  callbackCertificate, updatedAt
FROM psd2ClientConfiguration;
;

[SQL_INSERT_PSD2_CLIENT_CONFIGURATION]
INSERT INTO psd2ClientConfiguration (clientMode, applicationId, privateKeyPkcs8, callbackUrl,
  callbackPrivateKeyPkcs8, callbackCertificate, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?);
;

[SQL_UPDATE_PSD2_CLIENT_CONFIGURATION]
UPDATE psd2ClientConfiguration SET clientMode = ?, applicationId = ?, privateKeyPkcs8 = ?, callbackUrl = ?,
  callbackPrivateKeyPkcs8 = ?, callbackCertificate = ?, updatedAt = ?
WHERE id = ?;
