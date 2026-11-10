-- Why the market rejected an order, so rejections can be audited. Null for orders that were not rejected.
ALTER TABLE orders ADD COLUMN rejectionReason VARCHAR(30);
