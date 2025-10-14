package com.example.application.zoomdb;

import com.example.application.weld.CalcValues;
import com.example.application.diverse.camvas.GreetingComponent;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Route("lastview")
@PermitAll
@CssImport("./styles/shared-styles.css")
public class MainView extends HorizontalLayout {

    private final SvgImageRepository repository;
    private final String currentUser = "UserID";
    private final Div canvas = new Div();
    private final Div gallery = new Div();
    private final Input rotationInput = new Input();
    private final Button rotateButton = new Button("Roter valgt");
    private final Span apiResponse = new Span();

    public MainView(@Autowired SvgImageRepository repository) {
        this.repository = repository;
        setSizeFull();
        createMenu();

        gallery.setWidth("200px");
        gallery.getStyle().set("background", "#f0f0f0");
        gallery.getStyle().set("z-index", "5").set("position", "relative");
        add(gallery);

        canvas.setWidth("1200px");
        canvas.setHeight("860px");
        canvas.getStyle().set("border", "1px solid black");
        canvas.setId("svg-canvas");
        canvas.getElement().setProperty("innerHTML", "<svg id='main-canvas' width='1200' height='860' viewBox='0 0 1200 860' xmlns='http://www.w3.org/2000/svg'><g id='zoom-group'></g></svg>");
        add(canvas);

        refreshGallery();
    }

    private void createMenu() {
        VerticalLayout menu = new VerticalLayout();
        menu.setWidth("200px");
        menu.getStyle().set("z-index", "10").set("background", "black").set("color", "white");
        menu.add(new Span("Meny"));

        TextField zoomField = new TextField("Zoomfaktor");
        zoomField.setValue("1.0");
        Button zoomButton = new Button("Bruk zoom");
        zoomButton.addClickListener(e -> {
            String zoomValue = zoomField.getValue();
            UI.getCurrent().getPage().executeJs("""
                const svg = document.getElementById('active-svg');
                if (svg) {
                    const angle = svg.dataset.angle || '0';
                    svg.dataset.scale = $0;
                    svg.setAttribute('transform', `rotate(${angle}) scale($0)`);
                }
            """, zoomValue);
        });
        menu.add(zoomField, zoomButton);

        rotationInput.setPlaceholder("Vinkel");
        rotationInput.setValue("15");
        rotateButton.addClickListener(e -> rotateSelected());
        menu.add(rotationInput, rotateButton);


        menu.add(new Span("Meny"));
        TextField textField = new TextField("Tekst");
        Button addTextButton = new Button("Legg til tekst");
        addTextButton.addClickListener(e -> addMovableText(textField.getValue()));
        menu.add(textField, addTextButton);

        Button uploadButton = new Button("Last opp SVG");
        Upload upload = createUploadComponent();
        menu.add(uploadButton, upload);

        Button deleteButton = new Button("Slett valgt bilde");
        deleteButton.addClickListener(e -> UI.getCurrent().getPage().executeJs("""
            const svg = document.getElementById('active-svg');
            if (svg && svg.parentNode) {
                svg.parentNode.removeChild(svg);
            }
        """));
        menu.add(deleteButton);

        Button sendApi = new Button("Send til API");
        sendApi.addClickListener(e -> sendToApi());
        menu.add(sendApi);
        menu.add(apiResponse);

        add(menu);
    }
    private void addMovableText(String text) {
        UI.getCurrent().getPage().executeJs("""
            const canvas = document.getElementById('svg-canvas');
            let svg = canvas.querySelector('svg');
            if (!svg) {
                svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
                svg.setAttribute("width", "1200");
                svg.setAttribute("height", "860");
                canvas.appendChild(svg);
            }

            const textEl = document.createElementNS("http://www.w3.org/2000/svg", "text");
            textEl.setAttribute("x", "100");
            textEl.setAttribute("y", "100");
            textEl.setAttribute("fill", "black");
            textEl.setAttribute("font-size", "20");
            textEl.setAttribute("cursor", "move");
            textEl.textContent = $0;

            let isDragging = false;
            let offsetX = 0;
            let offsetY = 0;

            textEl.addEventListener('mousedown', (e) => {
                isDragging = true;
                offsetX = e.offsetX - parseFloat(textEl.getAttribute("x"));
                offsetY = e.offsetY - parseFloat(textEl.getAttribute("y"));
            });

            svg.addEventListener('mousemove', (e) => {
                if (isDragging) {
                    const pt = svg.createSVGPoint();
                    pt.x = e.clientX;
                    pt.y = e.clientY;
                    const svgP = pt.matrixTransform(svg.getScreenCTM().inverse());
                    textEl.setAttribute("x", svgP.x - offsetX);
                    textEl.setAttribute("y", svgP.y - offsetY);
                }
            });

            window.addEventListener('mouseup', () => {
                isDragging = false;
            });

            svg.appendChild(textEl);
        """, text);
    }

    private Upload createUploadComponent() {
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".svg");
        upload.addSucceededListener(event -> {
            try {
                InputStream input = buffer.getInputStream();
                String content = IOUtils.toString(input, StandardCharsets.UTF_8);
                SvgImage image = new SvgImage();
                image.setName(event.getFileName());
                image.setContent(content);
                image.setUserId(currentUser);
                image.setCreatedAt(LocalDateTime.now());
                repository.save(image);
                refreshGallery();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return upload;
    }

    private void refreshGallery() {
        gallery.removeAll();
        gallery.add(new Span("Galleri"));
        List<SvgImage> images = repository.findByUserId(currentUser);
        images.stream()
                .sorted(Comparator.comparing(SvgImage::getCreatedAt).reversed())
                .limit(4)
                .forEach(svg -> {
                    StreamResource resource = new StreamResource(svg.getName(), () -> new ByteArrayInputStream(svg.getContent().getBytes(StandardCharsets.UTF_8)));
                    Image img = new Image(resource, svg.getName());
                    img.setWidth("100px");
                    img.addClickListener(e -> addToCanvas(svg));
                    gallery.add(img);
                });
    }
    private void addToCanvas(SvgImage svg) {
        UI.getCurrent().getPage().executeJs("""
            const canvas = document.getElementById('svg-canvas');
            const parser = new DOMParser();
            const svgDoc = parser.parseFromString($0, "image/svg+xml").documentElement;
            svgDoc.style.position = 'absolute';
            svgDoc.style.left = '100px';
            svgDoc.style.top = '100px';
            svgDoc.setAttribute('id', 'active-svg');
            canvas.appendChild(svgDoc);
        """, svg.getContent());
    }
/*
    private void addToCanvas(SvgImage svg) {
        UI.getCurrent().getPage().executeJs("""
            const group = document.getElementById('zoom-group');
            const parser = new DOMParser();
            const doc = parser.parseFromString($0, "image/svg+xml");
            const originalSvg = doc.documentElement;

            const wrapper = document.createElementNS("http://www.w3.org/2000/svg", "g");
            wrapper.setAttribute("id", "active-svg");
            wrapper.dataset.angle = "0";
            wrapper.dataset.scale = "1";
            wrapper.dataset.tx = "0";
            wrapper.dataset.ty = "0";

            while (originalSvg.firstChild) {
                const child = originalSvg.firstChild;
                originalSvg.removeChild(child);
                wrapper.appendChild(child);
            }

            group.appendChild(wrapper);
        """, svg.getContent());
    }


 */
    private void rotateSelected() {
        try {
            int angle = Integer.parseInt(rotationInput.getValue());
            UI.getCurrent().getPage().executeJs("""
                const svg = document.getElementById('active-svg');
                if (svg) {
                    let current = parseInt(svg.dataset.angle || '0');
                    svg.dataset.angle = (current + $0) % 360;
                    const scale = svg.dataset.scale || 1;
                    svg.setAttribute('transform', `rotate(${svg.dataset.angle}) scale(${scale})`);
                }
            """, angle);
        } catch (NumberFormatException e) {
            rotationInput.setValue("15");
        }
    }

    private void sendToApi() {
        UI ui = UI.getCurrent();
        UI.getCurrent().getPage().executeJs("""
            const svg = document.getElementById('active-svg');
            if (!svg) return;

            const wrapper = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
            wrapper.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
            wrapper.setAttribute('viewBox', '0 0 1200 860');
            wrapper.setAttribute('width', '1200');
            wrapper.setAttribute('height', '860');
            wrapper.appendChild(svg.cloneNode(true));

            const serializer = new XMLSerializer();
            const content = serializer.serializeToString(wrapper);
            $1.$server.sendSvgToApi(content);
        """, getElement());
    }

    @ClientCallable
    public void sendSvgToApi(String content) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            HttpEntity<String> request = new HttpEntity<>(content, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity("https://weldit.weldit.no/api/images/" + CalcValues.userID, request, String.class);
            UI ui = UI.getCurrent();
            if (ui != null) {
                ui.access(() -> {
                    apiResponse.setText("Respons fra API: " + response.getStatusCode());
                });
            }
        } catch (Exception e) {
            UI.getCurrent().access(() -> {
                apiResponse.setText("Feil ved sending: " + e.getMessage());
            });
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        refreshGallery();
    }
}

