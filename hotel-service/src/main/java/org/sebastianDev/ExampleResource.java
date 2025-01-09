package org.sebastianDev;

// @Path("/hello")
public class ExampleResource {

    // @GET
    // @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus REST";
    }
}
