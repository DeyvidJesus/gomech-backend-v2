package com.gomech.api.modules.iam.application;

import com.gomech.api.modules.iam.infrastructure.oauth.GoogleIdTokenPayload;
import com.gomech.api.modules.iam.infrastructure.oauth.GoogleTokenResponse;

/**
 * Contrato da camada Application para integração com o provedor de identidade Google OAuth 2.0 / OIDC.
 */
public interface GoogleOAuthClient {

    /**
     * Troca o Authorization Code pelo conjunto de tokens do Google.
     *
     * @param code         Código de autorização recebido no callback
     * @param codeVerifier Verificador PKCE original gerado no handshake
     * @param redirectUri  URI de redirecionamento utilizada na inicialização
     * @return Resposta contendo id_token e access_token
     */
    GoogleTokenResponse exchangeCode(String code, String codeVerifier, String redirectUri);

    /**
     * Valida criptograficamente o ID Token OIDC e extrai os claims verificados.
     *
     * @param idToken       JWT bruto do Google ID Token
     * @param expectedNonce Nonce esperado contido no estado assinado
     * @return Payload de claims autenticados
     */
    GoogleIdTokenPayload verifyAndExtractIdToken(String idToken, String expectedNonce);
}
