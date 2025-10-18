package com.example.application.zoomdb;

import com.example.application.weld.CalcValues;
import com.example.application.diverse.camvas.GreetingComponent;
import com.example.application.diverse.camvas.Language;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;


@Route("lastviewsave")
@PermitAll
//@CssImport("./styles/shared-styles.css")
public class MainViewSave extends HorizontalLayout {

    private final SvgImageRepository repository;
    private final String currentUser = GreetingComponent.userIdents;
    private final Div canvas = new Div();
    private final Div gallery = new Div();
    private final Input rotationInput = new Input();
    private final Button rotateButton = new Button("Rotate selected image");
    private final Span apiResponse = new Span();
    private boolean eraserActive = false;
    HorizontalLayout menuBar = new HorizontalLayout();
    private boolean eraserMode = false;

    public MainViewSave(@Autowired SvgImageRepository repository) {
        this.repository = repository;
        setSizeFull();
        //     addClassName(LumoUtility.Background.CONTRAST_5);
        //       addClassName("menu-color");
        createHeader();

        createMenu();

        gallery.setWidth("200px");
        gallery.getStyle().set("background", "#ffffff");  // "#f0f0f0");
        gallery.getStyle().set("z-index", "5").set("position", "relative");
        add(gallery);

        canvas.setWidth("1200px");
        canvas.setHeight("860px");
        canvas.getStyle().set("border", "1px solid black");
        canvas.setId("svg-canvas");
        canvas.getElement().setProperty("innerHTML", "<svg id=\"main-canvas\" width=\"1200\" height=\"860\" viewBox=\"0 0 1200 860\" xmlns=\"http://www.w3.org/2000/svg\"><g id=\"zoom-group\"></g></svg>");
        add(canvas);

        refreshGallery();
    }

    // start generer meny
    private void createMenu() {

        VerticalLayout menu = new VerticalLayout();
        menu.setWidth("200px");
        menu.getStyle().set("z-index", "10").set("background", "#4e5d71").set("color", "white");  //  #4e5d71  #666a70

        Button home = new Button("Home");
        home.getStyle().set("color", "white");
        home.addClickListener(e -> UI.getCurrent().getPage().setLocation("home"));
        ;
        menu.add(home);

        Button exportButton = new Button("Send to API");
        exportButton.getStyle().set("color", "white");
        exportButton.addClickListener(e -> sendToApi());
        menu.add(exportButton);
        apiResponse.getStyle().set("color", "white");
        menu.add(apiResponse);
        add(menu);

        // tekst
        TextField textField = new TextField("");  // "Text"
        textField.setValue("Enter text inn here");
        textField.getStyle().set("color", "white");
        Button addTextButton = new Button("Add text to canvas");
        addTextButton.getStyle().set("color", "white");
        addTextButton.addClickListener(e -> {
            UI.getCurrent().getPage().executeJs("""
                        const svg = document.getElementById('main-canvas');
                        const text = document.createElementNS("http://www.w3.org/2000/svg", "text");
                        text.setAttribute("x", "100");
                        text.setAttribute("y", "100");
                        text.setAttribute("fill", "black");
                        text.setAttribute("font-size", "18");
                        text.setAttribute("cursor", "move");
                        text.textContent = $0;
                        svg.appendChild(text);
                    
                        let offsetX = 0, offsetY = 0, isDragging = false;
                        text.addEventListener('mousedown', (e) => {
                            isDragging = true;
                            offsetX = e.clientX - parseFloat(text.getAttribute('x'));
                            offsetY = e.clientY - parseFloat(text.getAttribute('y'));
                        });
                        window.addEventListener('mousemove', (e) => {
                            if (isDragging) {
                                text.setAttribute('x', e.clientX - offsetX);
                                text.setAttribute('y', e.clientY - offsetY);
                            }
                        });
                        window.addEventListener('mouseup', () => { isDragging = false; });
                    """, textField.getValue());
        });
        menu.add(textField, addTextButton);
        //rotate
        //    rotationInput.setPlaceholder("Vinkel (grader)");
        //    rotationInput.setValue("15");
        TextField rotationInput = new TextField("");     // Angle (grads)
        rotationInput.setValue("15");
        rotationInput.getStyle().set("color", "white");
        rotateButton.getStyle().set("color", "white");
        rotateButton.addClickListener(e -> {
            String angle = rotationInput.getValue();
            int vinkel = Integer.parseInt(angle);
            rotateSelected(vinkel);
            ;
        });
        menu.add(rotationInput, rotateButton);

// zoom
        TextField zoomFactor = new TextField("");   // Zoomfactor (0.1-3)
        zoomFactor.setValue("1.0");
        zoomFactor.getStyle().set("color", "white");
        Button zoomButton = new Button("Activate Zoom", e -> {
            String zoom = zoomFactor.getValue();

            UI.getCurrent().getPage().executeJs("""
                        const active = document.getElementById('active-svg');
                        if (active) {
                            let scale = parseFloat($0);
                            active.dataset.scale = scale;
                            updateTransform(active);
                        }
                        function updateTransform(el) {
                            const angle = el.dataset.angle || '0';
                            const scale = el.dataset.scale || '1';
                            const tx = el.dataset.tx || '0';
                            const ty = el.dataset.ty || '0';
                            el.setAttribute('transform', `translate(${tx},${ty}) rotate(${angle}) scale(${scale})`);
                        }
                    """, zoom);
        });


        zoomButton.getStyle().set("color", "white");
        menu.add(zoomFactor, zoomButton);

        /// ////////// 12
        Button saveButton = new Button("Save edited");
        saveButton.addClickListener(e -> saveEditedSvg());
        //   menu.add(saveButton);

        Button deleteButton = new Button("Delete selected image");
        deleteButton.getStyle().set("color", "white");
        deleteButton.addClickListener(e -> UI.getCurrent().getPage().executeJs("""
                    const svg = document.getElementById('active-svg');
                    if (svg && svg.parentNode) {
                        svg.parentNode.removeChild(svg);
                    }
                """));
        menu.add(deleteButton);
/*
        Button eraserButton = new Button("Activate Eraser");
        eraserButton.getStyle().set("color", "white");
        eraserButton.addClickListener(e -> toggleEraser());
          menu.add(eraserButton);
 */
        Button eraserButton = new Button("Activate Eraser");
        eraserButton.getStyle().set("color", "white");
        /*
        if (eraserActive) {
            eraserButton.setText("Deactivate Eraser");
        } else {
            eraserButton.setText("Activate Eraser");
        }

         */
        eraserButton.addClickListener(e -> toggleEraser());
        menu.add(eraserButton);

        Button viskelærKnapp = new Button("Eraser off/on");
        viskelærKnapp.getStyle().set("color", "white");
        viskelærKnapp.addClickListener(e -> {
            eraserActive = !eraserActive;
            UI.getCurrent().getPage().executeJs("""
                        window.eraserEnabled = $0;
                        const svg = document.getElementById("main-canvas");
                        if (eraserEnabled) {
                            svg.addEventListener("mousemove", eraserDraw);
                    
                        } else {
                            svg.removeEventListener("mousemove", eraserDraw);   
                        }
                    
                        function eraserDraw(event) {
                            if (!eraserEnabled || event.buttons !== 1) return;
                            const pt = svg.createSVGPoint();
                            pt.x = event.clientX;
                            pt.y = event.clientY;
                            const svgP = pt.matrixTransform(svg.getScreenCTM().inverse());
                    
                            const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
                                circle.style.cursor = "crosshair";
                            circle.setAttribute("cx", svgP.x);
                            circle.setAttribute("cy", svgP.y);
                            circle.setAttribute("r", "10");
                            circle.setAttribute("fill", "white");
                            svg.appendChild(circle);
                        }
                    """, eraserActive);
        });

        //     menu.add(viskelærKnapp);

        Button printButtonPart = new Button("Print");
        printButtonPart.getStyle().set("color", "white");
        printButtonPart.addClickListener(e -> printCanvasPart());
        menu.add(printButtonPart);

        Button downloadButton = new Button("Download as svg");
        downloadButton.getStyle().set("color", "white");
        downloadButton.addClickListener(e -> downloadCanvas());
        menu.add(downloadButton);

        Button pngButton = new Button("Download as PNG");
        pngButton.getStyle().set("color", "white");
        pngButton.addClickListener(e -> exportCanvasToPng());
        menu.add(pngButton);

        Button pdfButton = new Button("Download as PDF");
        pdfButton.getStyle().set("color", "white");
        pdfButton.addClickListener(e -> exportCanvasToPdf());
        menu.add(pdfButton);

        Button deleteOldButton = new Button("Delete old images");
        deleteOldButton.getStyle().set("color", "white");
        deleteOldButton.addClickListener(e -> deleteOldImages());
        //     menu.add(deleteOldButton);
/*
        //upload
        Upload upload = createUploadComponent();
        //   upload.getStyle().set("color", "white");
        menu.add(upload);


 */
    }
    // meny slutt

// rotate

    private void rotateSelected(int angle) {
        UI.getCurrent().getPage().executeJs("""
                    const active = document.getElementById('active-svg');
                    if (!active) return;
                
                    const svg = document.getElementById('main-canvas');
                
                    svg.addEventListener('click', function handler(evt) {
                        svg.removeEventListener('click', handler); // bare én gang
                
                        const pt = svg.createSVGPoint();
                        pt.x = evt.clientX;
                        pt.y = evt.clientY;
                        const cursorPt = pt.matrixTransform(svg.getScreenCTM().inverse());
                
                        let currentAngle = parseInt(active.dataset.angle || '0');
                        currentAngle = (currentAngle + $0) % 360;
                        active.dataset.angle = currentAngle;
                
                        const scale = active.dataset.scale || '1';
                        const tx = active.dataset.tx || '0';
                        const ty = active.dataset.ty || '0';
                
                        active.setAttribute('transform',
                            `translate(${tx},${ty}) rotate(${currentAngle}, ${cursorPt.x}, ${cursorPt.y}) scale(${scale})`);
                    });
                """, angle);
    }

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
                
                    let offsetX = 0, offsetY = 0, isDragging = false;
                
                    wrapper.addEventListener('mousedown', (e) => {
                        isDragging = true;
                        offsetX = e.clientX;
                        offsetY = e.clientY;
                        wrapper.style.cursor = "move";
                    });
                
                    window.addEventListener('mousemove', (e) => {
                        if (isDragging) {
                            const dx = e.clientX - offsetX;
                            const dy = e.clientY - offsetY;
                            offsetX = e.clientX;
                            offsetY = e.clientY;
                            let tx = parseFloat(wrapper.dataset.tx || '0');
                            let ty = parseFloat(wrapper.dataset.ty || '0');
                            tx += dx;
                            ty += dy;
                            wrapper.dataset.tx = tx;
                            wrapper.dataset.ty = ty;
                            updateTransform();
                        }
                    });
                
                    window.addEventListener('mouseup', () => {
                        isDragging = false;
                    });
                
                    wrapper.addEventListener('wheel', (e) => {
                        e.preventDefault();
                        let scale = parseFloat(wrapper.dataset.scale || '1');
                        scale += e.deltaY < 0 ? 0.1 : -0.1;
                        scale = Math.max(0.1, Math.min(scale, 5));
                        wrapper.dataset.scale = scale;
                        updateTransform();
                    });
                
                    function updateTransform() {
                        const angle = wrapper.dataset.angle || '0';
                        const scale = wrapper.dataset.scale || '1';
                        const tx = wrapper.dataset.tx || '0';
                        const ty = wrapper.dataset.ty || '0';
                        wrapper.setAttribute('transform', `translate(${tx},${ty}) rotate(${angle}) scale(${scale})`);
                    }
                    group.appendChild(wrapper);
                """, svg.getContent());

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
        Upload upload = createUploadComponent();
        //   upload.getStyle().set("color", "white");
        gallery.add(upload);
        gallery.add(new Span("Gallery"));
        List<SvgImage> images = repository.findByUserId(currentUser);
        images.stream()
                .sorted(Comparator.comparing(SvgImage::getCreatedAt).reversed())
                .limit(5)
                .forEach(svg -> {
                    StreamResource resource = new StreamResource(svg.getName(), () -> new ByteArrayInputStream(svg.getContent().getBytes(StandardCharsets.UTF_8)));
                    Image img = new Image(resource, svg.getName());
                    img.setWidth("200px");
                    img.addClickListener(e -> addToCanvas(svg));
                    gallery.add(img);
                });
    }

    private void exportCanvas(String type) {
        UI.getCurrent().getPage().executeJs("""
                    const svg = document.getElementById('svg-canvas');
                    if (!svg) return;
                    const canvas = document.createElement('canvas');
                    const ctx = canvas.getContext('2d');
                    const data = new XMLSerializer().serializeToString(svg);
                    const blob = new Blob([data], {type: 'image/svg+xml;charset=utf-8'});
                    const url = URL.createObjectURL(blob);
                    const img = new Image();
                    img.onload = function() {
                        canvas.width = img.width;
                        canvas.height = img.height;
                        ctx.drawImage(img, 0, 0);
                        const link = document.createElement('a');
                        link.download = 'exported.' + arguments[0];
                        link.href = canvas.toDataURL('image/' + arguments[0]);
                        link.click();
                        URL.revokeObjectURL(url);
                    };
                    img.src = url;
                """, type);
    }

    private void applyZoom(String value) {
        UI.getCurrent().getPage().executeJs(
                "const svg = document.getElementById('active-svg'); if(svg) { svg.style.transform = `scale(${value})`; }"
        );
    }

    private void exportCanvasToPng() {
        UI.getCurrent().getPage().executeJs("""
                    const svgElement = document.getElementById("main-canvas");
                    const svgData = new XMLSerializer().serializeToString(svgElement);
                    const canvas = document.createElement("canvas");
                    canvas.width = svgElement.clientWidth;
                    canvas.height = svgElement.clientHeight;
                    const ctx = canvas.getContext("2d");
                
                    const img = new Image();
                    const svgBlob = new Blob([svgData], {type: "image/svg+xml;charset=utf-8"});
                    const url = URL.createObjectURL(svgBlob);
                
                    img.onload = () => {
                        ctx.drawImage(img, 0, 0);
                        URL.revokeObjectURL(url);
                
                        const pngUrl = canvas.toDataURL("image/png");
                        const a = document.createElement("a");
                        a.href = pngUrl;
                        a.download = "tegneomrade.png";
                        document.body.appendChild(a);
                        a.click();
                        document.body.removeChild(a);
                    };
                    img.src = url;
                """);
    }

    private void exportCanvasToPdf() {
        UI.getCurrent().getPage().executeJs("""
                    const svgElement = document.getElementById("main-canvas");
                    const svgData = new XMLSerializer().serializeToString(svgElement);
                    const canvas = document.createElement("canvas");
                    canvas.width = svgElement.clientWidth;
                    canvas.height = svgElement.clientHeight;
                    const ctx = canvas.getContext("2d");
                
                    const img = new Image();
                    const svgBlob = new Blob([svgData], {type: "image/svg+xml;charset=utf-8"});
                    const url = URL.createObjectURL(svgBlob);
                
                    img.onload = () => {
                        ctx.drawImage(img, 0, 0);
                        URL.revokeObjectURL(url);
                
                        const imgData = canvas.toDataURL("image/png");
                        const pdf = new jspdf.jsPDF({
                            orientation: "landscape",
                            unit: "pt",
                            format: [canvas.width, canvas.height]
                        });
                        pdf.addImage(imgData, 'PNG', 0, 0, canvas.width, canvas.height);
                        pdf.save("tegneomrade.pdf");
                    };
                    img.src = url;
                """);
    }

    private void downloadCanvas() {
        UI.getCurrent().getPage().executeJs("""
                    const svg = document.getElementById('main-canvas');
                    if (!svg) return;
                    const clone = svg.cloneNode(true);
                    clone.removeAttribute('id');
                    clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
                    const serializer = new XMLSerializer();
                    const content = serializer.serializeToString(clone);
                    const blob = new Blob([content], {type: "image/svg+xml"});
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'tegneomrade.svg';
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    URL.revokeObjectURL(url);
                """);
    }

    private void sendToApi() {
        UI.getCurrent().getPage().executeJs("""
                    const svg = document.getElementById('main-canvas');
                    if (!svg) return;
                    const clone = svg.cloneNode(true);
                    clone.removeAttribute('id');
                    clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
                
                    const serializer = new XMLSerializer();
                    const content = serializer.serializeToString(clone);
                    $0.$server.sendSvgToApi(content);
                """, getElement());
    }

    @ClientCallable
    public void sendSvgToApi(String content) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            HttpEntity<String> request = new HttpEntity<>(content, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity("https://weldit.weldit.no/api/images", request, String.class);
            UI ui = UI.getCurrent();
            if (ui != null) {
                ui.access(() -> apiResponse.setText("Respons from API: " + response.getStatusCode()));
            }
        } catch (Exception e) {
            UI ui = UI.getCurrent();
            if (ui != null) {
                ui.access(() -> apiResponse.setText("Error by sending: " + e.getMessage()));
            }
        }
    }

    private void saveEditedSvg() {
        UI.getCurrent().getPage().executeJs("""
                    const svg = document.getElementById('main-canvas');
                    if (!svg) return;
                    const clone = svg.cloneNode(true);
                    clone.removeAttribute('id');
                    clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
                
                    const serializer = new XMLSerializer();
                    const content = serializer.serializeToString(clone);
                    $0.$server.saveSvgFromClient(content);
                """, getElement());
    }

    private void printCanvas() {
        UI.getCurrent().getPage().executeJs("window.print();");
    }

    private void printCanvasPart() {
        UI.getCurrent().getPage().executeJs("""
                    const canvas = document.getElementById('svg-canvas');
                    const svg = canvas.querySelector('svg');
                    if (!svg) return;
                
                    const win = window.open('', '', 'width=1300,height=900');
                    win.document.write('<html><head><title>Utskrift</title></head><body style="margin:0;">');
                    win.document.write(svg.outerHTML);
                    win.document.write('</body></html>');
                    win.document.close();
                    win.focus();
                    setTimeout(() => win.print(), 500);
                """);
    }

    private void deleteOldImages() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusWeeks(4);
        List<SvgImage> oldImages = repository.findByCreatedAtBefore(cutoffDate);

        if (!oldImages.isEmpty()) {
            repository.deleteAll(oldImages);
            Notification.show(oldImages.size() + " old images deleted.");
        } else {
            Notification.show("No old images found.");
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        refreshGallery();
    }


    public void createHeader() {
        H1 title = new H1("Weld IT");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-l)");
        // Menyvalgene
        // RouterLink home = new RouterLink("Hjem", Home.class);
        Image imga = new Image("icons/logo.png", "logo");
        imga.setWidth("40px");
        imga.addClickListener(click -> {
            UI.getCurrent().getPage().setLocation("home");
        });

        Button skille = new Button("*");
        skille.setWidth("10px");
        Button home = new Button("Hpme");
        home.setWidth("40px");
        home.addClickListener(click -> {
            UI.getCurrent().getPage().setLocation("home");
        });

        menuBar = new HorizontalLayout(imga, home);

    }

/*
    private void toggleEraser() {
        eraserMode = !eraserMode;
        UI.getCurrent().getPage().executeJs("""
        const svg = document.getElementById("main-canvas");
        if (!svg) return;
        
        if ($0) {
            svg.style.cursor = "crosshair";
            svg.addEventListener("click", eraserHandler);
        } else {
            svg.style.cursor = "default";
            svg.removeEventListener("click", eraserHandler);
        }

        function eraserHandler(e) {
            const target = e.target;
            if (target && target.closest("g") && target.closest("g").id === "active-svg") {
                target.remove();
            }
        }
    """, eraserMode);
    }


 */


    private void toggleEraser() {
        eraserActive = !eraserActive;
        if (eraserActive) {
            UI.getCurrent().getPage().executeJs("""
                        const svg = document.getElementById('main-canvas');
                        if (!svg) return;
                    
                        svg.style.cursor = 'crosshair';
                    
                        window.eraserClickHandler = function (e) {
                            if (e.target && e.target.tagName !== 'svg' && e.target.id !== 'zoom-group') {
                                e.target.remove();
                            }
                        };
                    
                        svg.addEventListener('click', window.eraserClickHandler);
                    """);
        } else {
            UI.getCurrent().getPage().executeJs("""
                        const svg = document.getElementById('main-canvas');
                        if (!svg) return;
                    
                        svg.style.cursor = 'default';
                        if (window.eraserClickHandler) {
                            svg.removeEventListener('click', window.eraserClickHandler);
                            window.eraserClickHandler = null;
                        }
                    """);
        }
    }
}


