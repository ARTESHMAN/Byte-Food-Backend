package org.croissantbuddies.snappfood.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class JwtUtil {
    private static final String SECRET = "NoneOfUrFuckingBusiness$$$2005-2006";
    private static final long EXPIRATION_TIME = 3600000;

    private static final Algorithm algorithm = Algorithm.HMAC256(SECRET);

    public static String generateToken(Long userId, String role) {
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("role", role)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(algorithm);
    }

    public static DecodedJWT verifyToken(String token) {
        JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }

    public static Long getUserIdFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return Long.parseLong(jwt.getSubject());
    }

    public static class TokenWhitelist {
        private static final Set<String> whitelist = new HashSet<>();

        public static void add(String token) {
            whitelist.add(token);
        }

        public static boolean contains(String token) {
            return whitelist.contains(token);
        }

        public static void remove(String token) {
            whitelist.remove(token);
        }
    }
    public static String getRoleFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return jwt.getClaim("role").asString();
    }
}
