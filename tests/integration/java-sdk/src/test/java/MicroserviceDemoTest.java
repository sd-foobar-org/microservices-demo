import com.signadot.ApiClient;
import com.signadot.ApiException;
import com.signadot.api.WorkspacesApi;
import com.signadot.model.*;
import hipstershop.AdServiceGrpc;
import hipstershop.Demo;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.util.List;
public class MicroserviceDemoTest {

    public static final String ORG_NAME = "sd-foobar-org";
    public static final String NAMESPACE = "default";
    public static final String SIGNADOT_API_KEY = System.getenv("SIGNADOT_API_KEY");
    private static RequestSpecification requestSpec;

    ApiClient apiClient;
    WorkspacesApi workspacesApi;
    CreateWorkspaceResponse response;
    String workspaceID;

    @BeforeSuite
    public void createWorkspace() throws ApiException, InterruptedException {
        apiClient = new ApiClient();
        apiClient.setApiKey(SIGNADOT_API_KEY);
        workspacesApi = new WorkspacesApi(apiClient);

        WorkspaceFork adServiceFork = new WorkspaceFork()
                .forkOf(new ForkOf().kind("Deployment").namespace(NAMESPACE).name("adservice"))
                .customizations(new WorkspaceCustomizations()
                        .addImagesItem(new Image().image("jmsktm/microservices-demo-adservice:7eb9c24f0c37504d3a05e1bd81756b785d8d0791")))
                .addEndpointsItem(new ForkEndpoint().name("adservice").port(9555).protocol("grpc"));

        CreateWorkspaceRequest request = new CreateWorkspaceRequest()
                .cluster("personal-mac")
                .name("ad-carousel-ws")
                .description("Return 3 ads and display in carousel")
                .addForksItem(adServiceFork);

        response = workspacesApi.createNewWorkspace(ORG_NAME, request);

        workspaceID = response.getWorkspaceID();
        if (workspaceID == null || workspaceID == "") {
            throw new RuntimeException("Workspace ID not set in API response");
        }

        List<PreviewEndpoint> endpoints = response.getPreviewEndpoints();
        if (endpoints.size() == 0) {
            throw new RuntimeException("preview endpoints not available in API response");
        }

        PreviewEndpoint endpoint = null;
        for (PreviewEndpoint ep: endpoints) {
            if ("adservice".equals(ep.getName())) {
                endpoint = ep;
                break;
            }
        }
        if (endpoint == null) {
            throw new RuntimeException("No endpoint found for ad service");
        }

        // set the base URL for tests
        RestAssured.baseURI = endpoint.getPreviewURL();

        RequestSpecBuilder builder = new RequestSpecBuilder();
        builder.setBaseUri(endpoint.getPreviewURL());
        builder.addHeader("signadot-api-key", SIGNADOT_API_KEY);

        requestSpec = builder.build();

        // Check for workspace readiness
        while (!workspacesApi.getWorkspaceReady(ORG_NAME, workspaceID).isReady()) {
            Thread.sleep(5000);
        };
    }

    @Test
    public void testFor3Ads() {
        // Run the test
        // Demo.AdRequest adRequest = Demo.AdRequest.getDefaultInstance();
    }

    @AfterSuite
    public void deleteWorkspace() throws ApiException {
        workspacesApi.deleteWorkspaceById(ORG_NAME, workspaceID);
    }
}