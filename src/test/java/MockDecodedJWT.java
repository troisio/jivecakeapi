import java.util.Date;
import java.util.List;
import java.util.Map;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

public class MockDecodedJWT implements DecodedJWT {
    @Override
    public String getIssuer() {
        return null;
    }

    @Override
    public String getSubject() {
        return null;
    }

    @Override
    public List<String> getAudience() {
        return null;
    }

    @Override
    public Date getExpiresAt() {
        return null;
    }

    @Override
    public Date getNotBefore() {
        return null;
    }

    @Override
    public Date getIssuedAt() {
        return null;
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public Claim getClaim(String name) {
        return null;
    }

    @Override
    public Map<String, Claim> getClaims() {
        return null;
    }

    @Override
    public String getAlgorithm() {
        return null;
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public String getKeyId() {
        return null;
    }

    @Override
    public Claim getHeaderClaim(String name) {
        return null;
    }

    @Override
    public String getToken() {
        return null;
    }

    @Override
    public String getHeader() {
        return null;
    }

    @Override
    public String getPayload() {
        return null;
    }

    @Override
    public String getSignature() {
        return null;
    }

}
