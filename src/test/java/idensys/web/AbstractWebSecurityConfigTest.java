package idensys.web;


import idensys.AbstractIntegrationTest;
import org.apache.commons.io.IOUtils;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.security.SecurityException;
import org.opensaml.xml.signature.SignatureException;
import org.springframework.http.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static org.junit.Assert.*;

public abstract class AbstractWebSecurityConfigTest extends AbstractIntegrationTest {

  protected String entityId ="https://engine.test2.surfconext.nl/authentication/sp/metadata";

  protected String acsLocation = "https://engine.test2.surfconext.nl/authentication/sp/consume-assertion";

  protected String getSAMLResponseForError(ResponseEntity<String> response) {
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

    Matcher matcher = Pattern.compile("name=\"SAMLResponse\" value=\"(.*?)\"").matcher(response.getBody());
    assertTrue(matcher.find());

    return new String(Base64.getDecoder().decode(matcher.group(1)));
  }

  protected void assertAuthResponse(ResponseEntity<String> response) {
    String samlResponse = decodeSamlPost(response, true);

    assertTrue(samlResponse.contains("Destination=\""+ acsLocation +"\""));
  }

  protected String decodeSamlPost(ResponseEntity<String> response, boolean isResponse) {
    assertEquals(200, response.getStatusCode().value());

    String html = response.getBody();

    String samlType = isResponse ? "SAMLResponse" : "SAMLRequest";
    Matcher matcher = Pattern.compile("name=\"" + samlType + "\" value=\"(.*?)\"").matcher(html);
    assertTrue(matcher.find());

    String samlBase64Encoded = matcher.group(1);
    return new String(Base64.getDecoder().decode(samlBase64Encoded));
  }

  protected String decodeSamlArtifactRedirect(ResponseEntity<String> response) throws URISyntaxException {
    String location = response.getHeaders().getLocation().toString();
    //https://eid.digidentity-accept.eu/hm/eh19/dv_hm?SAMLart=AAQAABxeJBIvD4dLuuvHbWYzCZ37qlMHy5jK%2BDF9P1CY6bNnxgEPfOJftCc%3D
    return queryParameters(location).get("SAMLart");
  }

  protected String decodeSamlRedirect(ResponseEntity<String> response) throws URISyntaxException, IOException {
    String location = response.getHeaders().getLocation().toString();

    Map<String, String> queryParameters = queryParameters(location);
    byte[] decodedBytes = Base64.getDecoder().decode(queryParameters.get("SAMLRequest"));

    return IOUtils.toString(new InflaterInputStream(new ByteArrayInputStream(decodedBytes), new Inflater(true)));
  }

  protected void assertInvalidResponse(String entity, String acs, String expectedErrorMessage) throws SecurityException, MessageEncodingException, SignatureException, MarshallingException, UnknownHostException {
    String url = samlRequestUtils.redirectUrl(entity, "http://localhost:" + port + "/", acs, Optional.empty(), true);
    doAssertInvalidResponse(expectedErrorMessage, url);
  }

  protected void doAssertInvalidResponse(String expectedErrorMessage, String url) {
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    String saml = getSAMLResponseForError(response);

    assertTrue(saml.contains(expectedErrorMessage));
    assertFalse(saml.contains("Subject"));
  }

}