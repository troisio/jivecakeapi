package com.jivecake.api.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.jivecake.api.model.Translation;

@Path("localisation")
public class LocalisationResource {
    private final Map<String, List<Translation>> translationGroups;

    @Inject
    public LocalisationResource() throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get("resource/localisation/translation.txt"));
        String file =  new String(encoded, "UTF-8");
        String[] groups = file.split("[\\r\\n]{2}");

        this.translationGroups = Arrays.asList(groups)
            .stream()
            .map(group -> {
                String[] lines = group.split("[\\r\\n]");

                return Arrays.asList(lines)
                    .stream()
                    .map(line -> {
                        int index = line.indexOf(":");
                        Translation translation = new Translation();
                        translation.code = line.substring(0, index);
                        translation.text = line.substring(index + 2);
                        return translation;
                    })
                    .collect(Collectors.toList());
            })
            .collect(
                Collectors.toMap(
                    translations -> translations.get(0).text,
                    Function.identity()
                )
            );
    }

    @GET
    public Response getLocalisation() throws IOException {
        return Response.ok(this.translationGroups, MediaType.APPLICATION_JSON).build();
    }
}
