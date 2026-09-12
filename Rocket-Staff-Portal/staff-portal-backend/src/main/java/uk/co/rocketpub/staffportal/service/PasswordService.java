package uk.co.rocketpub.staffportal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    public static final String TEMPORARY_PASSWORD = "Password";

    private static final String SALT_CHARACTERS =
            "abcdefghijklmnopqrstuvwxyz"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            + "0123456789";

    private final SecureRandom secureRandom = new SecureRandom();

    // Checks a password against Flask/Werkzeug's scrypt format.
    public boolean matches(String password, String storedHash) {

        if (password == null
                || storedHash == null
                || !storedHash.startsWith("scrypt:")) {

            return false;
        }

        try {
            String[] parts = storedHash.split("\\$", 3);

            if (parts.length != 3) {
                return false;
            }

            String[] method = parts[0].split(":");

            if (method.length != 4) {
                return false;
            }

            int n = Integer.parseInt(method[1]);
            int r = Integer.parseInt(method[2]);
            int p = Integer.parseInt(method[3]);

            String salt = parts[1];
            byte[] expectedHash = fromHex(parts[2]);

            byte[] calculatedHash = SCrypt.generate(
                    password.getBytes(StandardCharsets.UTF_8),
                    salt.getBytes(StandardCharsets.UTF_8),
                    n,
                    r,
                    p,
                    expectedHash.length
            );

            return MessageDigest.isEqual(
                    expectedHash,
                    calculatedHash
            );

        } catch (RuntimeException exception) {
            return false;
        }
    }

    // Generates the same scrypt format used by modern Werkzeug.
    public String generate(String password) {

        int n = 32768;
        int r = 8;
        int p = 1;

        String salt = generateSalt(16);

        byte[] hash = SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8),
                salt.getBytes(StandardCharsets.UTF_8),
                n,
                r,
                p,
                64
        );

        return "scrypt:"
                + n
                + ":"
                + r
                + ":"
                + p
                + "$"
                + salt
                + "$"
                + toHex(hash);
    }

    private String generateSalt(int length) {
        StringBuilder salt = new StringBuilder(length);

        for (int index = 0; index < length; index++) {
            salt.append(
                    SALT_CHARACTERS.charAt(
                            secureRandom.nextInt(
                                    SALT_CHARACTERS.length()
                            )
                    )
            );
        }

        return salt.toString();
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();

        for (byte value : bytes) {
            result.append(
                    String.format("%02x", value & 0xff)
            );
        }

        return result.toString();
    }

    private byte[] fromHex(String value) {

        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Invalid hexadecimal hash"
            );
        }

        byte[] result = new byte[value.length() / 2];

        for (int index = 0;
                index < value.length();
                index += 2) {

            result[index / 2] = (byte) Integer.parseInt(
                    value.substring(index, index + 2),
                    16
            );
        }

        return result;
    }
}
