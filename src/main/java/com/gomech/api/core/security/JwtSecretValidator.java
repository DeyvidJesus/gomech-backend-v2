package com.gomech.api.core.security;

import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fails application startup when the JWT signing secret is unsafe, so a deployment that forgot to
 * set {@code JWT_SECRET} refuses to boot instead of signing tokens with a value anyone can read.
 *
 * <p>Only the {@code local} profile ships a default secret (see {@code application-local.yml}).
 * Every other profile must receive {@code JWT_SECRET} from the environment, and the value must be
 * Base64 (hex output of {@code openssl rand -hex 32} is valid Base64), decode to at least 256 bits
 * (the HMAC-SHA256 minimum {@link JwtUtil} needs), and not be one of the publicly known values.
 */
@Component
public class JwtSecretValidator {

    static final int MIN_KEY_BYTES = 32;

    /** Values published in this repository or in well-known tutorials; accepted only locally. */
    static final Set<String> PUBLICLY_KNOWN_SECRETS = Set.of(
        "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "e39125fc5add83ed45a24dbe4fa687d1f10bbefb5fb66d1eb44c48180325c342"
    );

    public JwtSecretValidator(@Value("${jwt.secret:}") String secret, Environment environment) {
        validate(secret, environment.acceptsProfiles(Profiles.of("local")));
    }

    static void validate(String secret, boolean localProfile) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET não está configurado. Defina um segredo forte (por exemplo, openssl rand -hex 32); "
                    + "somente o profile 'local' tem valor padrão.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET precisa estar em Base64 ou hexadecimal.", e);
        }

        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(String.format(
                "JWT_SECRET é curto demais: %d bytes após decodificar, mínimo de %d (256 bits).",
                keyBytes.length, MIN_KEY_BYTES));
        }

        if (!localProfile && PUBLICLY_KNOWN_SECRETS.contains(secret.trim())) {
            throw new IllegalStateException(
                "JWT_SECRET usa um valor público de desenvolvimento, aceito apenas no profile 'local'. "
                    + "Gere um segredo exclusivo para este ambiente.");
        }
    }
}
