package de.zft2.gbanking.enablebanking;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

final class EnablebankingDer {

	private EnablebankingDer() {
	}

	static byte[] sequence(byte[]... values) {
		return tagged(0x30, concatenate(values));
	}

	static byte[] explicit(int tagNumber, byte[] value) {
		return tagged(0xa0 + tagNumber, value);
	}

	static byte[] integer(BigInteger value) {
		return tagged(0x02, value.toByteArray());
	}

	static byte[] oid(String value) {
		String[] parts = value.split("\\.");
		ByteArrayOutputStream encoded = new ByteArrayOutputStream();
		writeOidPart(encoded, Long.parseLong(parts[0]) * 40 + Long.parseLong(parts[1]));
		for (int index = 2; index < parts.length; index++) {
			writeOidPart(encoded, Long.parseLong(parts[index]));
		}
		return tagged(0x06, encoded.toByteArray());
	}

	static byte[] nullValue() {
		return tagged(0x05, new byte[0]);
	}

	static byte[] octetString(byte[] value) {
		return tagged(0x04, value);
	}

	static byte[] bitString(byte[] value, int unusedBits) {
		byte[] encoded = new byte[value.length + 1];
		encoded[0] = (byte) unusedBits;
		System.arraycopy(value, 0, encoded, 1, value.length);
		return tagged(0x03, encoded);
	}

	static byte[] bool(boolean value) {
		return tagged(0x01, new byte[] { value ? (byte) 0xff : 0 });
	}

	static byte[] ascii(int tag, String value) {
		return tagged(tag, value.getBytes(StandardCharsets.US_ASCII));
	}

	static byte[] tagged(int tag, byte[] value) {
		ByteArrayOutputStream encoded = new ByteArrayOutputStream();
		encoded.write(tag);
		writeLength(encoded, value.length);
		encoded.writeBytes(value);
		return encoded.toByteArray();
	}

	private static byte[] concatenate(byte[][] values) {
		ByteArrayOutputStream encoded = new ByteArrayOutputStream();
		for (byte[] value : values) {
			encoded.writeBytes(value);
		}
		return encoded.toByteArray();
	}

	private static void writeLength(ByteArrayOutputStream target, int length) {
		if (length < 0x80) {
			target.write(length);
			return;
		}
		int byteCount = Integer.BYTES - Integer.numberOfLeadingZeros(length) / Byte.SIZE;
		target.write(0x80 | byteCount);
		for (int shift = (byteCount - 1) * Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
			target.write(length >>> shift);
		}
	}

	private static void writeOidPart(ByteArrayOutputStream target, long value) {
		int byteCount = Math.max(1, (Long.SIZE - Long.numberOfLeadingZeros(value) + 6) / 7);
		for (int shift = (byteCount - 1) * 7; shift >= 0; shift -= 7) {
			int encoded = (int) (value >>> shift) & 0x7f;
			target.write(shift == 0 ? encoded : encoded | 0x80);
		}
	}
}
