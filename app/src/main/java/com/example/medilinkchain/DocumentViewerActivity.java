package com.example.medilinkchain;

import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DocumentViewerActivity extends AppCompatActivity {

    private static final String TAG = "DocumentViewerActivity";

    public static final String EXTRA_DOCUMENT_URL = "EXTRA_DOCUMENT_URL";
    public static final String EXTRA_STORAGE_PATH = "EXTRA_STORAGE_PATH";
    public static final String EXTRA_DOCUMENT_NAME = "EXTRA_DOCUMENT_NAME";
    public static final String EXTRA_DOCUMENT_DATE = "EXTRA_DOCUMENT_DATE";
    public static final String EXTRA_DOCUMENT_TYPE = "EXTRA_DOCUMENT_TYPE";

    // UI Views
    private ZoomableImageView zoomableDocView;
    private TextView tvDocName;
    private TextView tvDocDate;
    private TextView tvDocTypeBadge;
    private LinearLayout layoutLoading;
    private TextView tvLoadingMessage;
    private LinearLayout layoutError;
    private TextView tvErrorDetails;
    private MaterialButton btnTryAgain;
    private LinearLayout layoutPdfPageNav;
    private MaterialButton btnPrevPage;
    private MaterialButton btnNextPage;
    private TextView tvPageIndicator;
    private MaterialButton btnZoomIn;
    private MaterialButton btnZoomOut;
    private MaterialButton btnDownload;
    private MaterialButton btnClose;
    private ImageView btnBack;
    private ImageView btnHeaderDownload;

    // Document & Render state
    private String rawUrl = "";
    private String storagePath = "";
    private String docName = "Medical Document";
    private String docDate = "";
    private String docType = "";

    private File cachedFile = null;
    private PdfRenderer pdfRenderer = null;
    private ParcelFileDescriptor pfd = null;
    private int currentPdfPage = 0;
    private int totalPdfPages = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_viewer);

        initViews();
        extractIntentData();
        setupListeners();

        loadAndRenderDocument();
    }

    private void initViews() {
        zoomableDocView = findViewById(R.id.zoomableDocView);
        tvDocName = findViewById(R.id.tvDocName);
        tvDocDate = findViewById(R.id.tvDocDate);
        tvDocTypeBadge = findViewById(R.id.tvDocTypeBadge);
        layoutLoading = findViewById(R.id.layoutLoading);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage);
        layoutError = findViewById(R.id.layoutError);
        tvErrorDetails = findViewById(R.id.tvErrorDetails);
        btnTryAgain = findViewById(R.id.btnTryAgain);
        layoutPdfPageNav = findViewById(R.id.layoutPdfPageNav);
        btnPrevPage = findViewById(R.id.btnPrevPage);
        btnNextPage = findViewById(R.id.btnNextPage);
        tvPageIndicator = findViewById(R.id.tvPageIndicator);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        btnDownload = findViewById(R.id.btnDownload);
        btnClose = findViewById(R.id.btnClose);
        btnBack = findViewById(R.id.btnBack);
        btnHeaderDownload = findViewById(R.id.btnHeaderDownload);
    }

    private void extractIntentData() {
        rawUrl = getIntent().getStringExtra(EXTRA_DOCUMENT_URL);
        storagePath = getIntent().getStringExtra(EXTRA_STORAGE_PATH);
        docName = getIntent().getStringExtra(EXTRA_DOCUMENT_NAME);
        docDate = getIntent().getStringExtra(EXTRA_DOCUMENT_DATE);
        docType = getIntent().getStringExtra(EXTRA_DOCUMENT_TYPE);

        if (docName == null || docName.trim().isEmpty()) {
            docName = "Medical Document";
        }
        if (docDate == null || docDate.trim().isEmpty()) {
            docDate = "Date: Not specified";
        } else if (!docDate.toLowerCase().startsWith("uploaded") && !docDate.toLowerCase().startsWith("date")) {
            docDate = "Uploaded: " + docDate;
        }

        tvDocName.setText(docName);
        tvDocDate.setText(docDate);

        // Detect or display document badge
        String badge = inferDocTypeBadge(docName, docType, rawUrl);
        tvDocTypeBadge.setText(badge);
    }

    private String inferDocTypeBadge(String name, String type, String url) {
        String lowerName = (name + " " + type + " " + url).toLowerCase();
        if (lowerName.contains(".pdf") || "pdf".equalsIgnoreCase(type)) {
            return "PDF";
        } else if (lowerName.contains(".png") || "png".equalsIgnoreCase(type)) {
            return "PNG";
        } else if (lowerName.contains(".webp") || "webp".equalsIgnoreCase(type)) {
            return "WEBP";
        } else if (lowerName.contains(".jpg") || lowerName.contains(".jpeg") || "jpg".equalsIgnoreCase(type) || "jpeg".equalsIgnoreCase(type)) {
            return "JPG";
        }
        return "DOCUMENT";
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnClose.setOnClickListener(v -> finish());

        btnZoomIn.setOnClickListener(v -> {
            if (zoomableDocView != null) zoomableDocView.zoomIn();
        });

        btnZoomOut.setOnClickListener(v -> {
            if (zoomableDocView != null) zoomableDocView.zoomOut();
        });

        btnPrevPage.setOnClickListener(v -> {
            if (pdfRenderer != null && currentPdfPage > 0) {
                renderPdfPage(currentPdfPage - 1);
            }
        });

        btnNextPage.setOnClickListener(v -> {
            if (pdfRenderer != null && currentPdfPage < totalPdfPages - 1) {
                renderPdfPage(currentPdfPage + 1);
            }
        });

        btnDownload.setOnClickListener(v -> downloadDocumentFile());
        btnHeaderDownload.setOnClickListener(v -> downloadDocumentFile());

        btnTryAgain.setOnClickListener(v -> loadAndRenderDocument());
    }

    // =========================================================================
    // Document Retrieval & Rendering
    // =========================================================================

    private void loadAndRenderDocument() {
        showLoading(true, "Loading document...");
        layoutError.setVisibility(View.GONE);
        zoomableDocView.setVisibility(View.INVISIBLE);
        layoutPdfPageNav.setVisibility(View.GONE);

        // If storagePath is provided and rawUrl is missing, obtain signed URL first
        if ((rawUrl == null || rawUrl.isEmpty()) && (storagePath != null && !storagePath.isEmpty())) {
            SupabaseStorageHelper.createSignedUrl(storagePath, 3600, new SupabaseStorageHelper.SignedUrlCallback() {
                @Override
                public void onSuccess(String signedUrl) {
                    rawUrl = signedUrl;
                    downloadAndDisplayFile(signedUrl);
                }

                @Override
                public void onFailure(String errorMessage) {
                    showError("Unable to load document", "Could not generate secure storage access URL: " + errorMessage);
                }
            });
        } else if (rawUrl != null && !rawUrl.isEmpty()) {
            downloadAndDisplayFile(rawUrl);
        } else {
            showError("Document Unavailable", "No document URL or storage path was provided.");
        }
    }

    private void downloadAndDisplayFile(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MediLinkChain-Android-App/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Document download failed: " + e.getMessage());
                showError("Unable to load document", "Failed to retrieve the document from secure storage. Please verify your connection.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    response.close();
                    showError("Unable to load document", "Secure storage returned response code: " + response.code());
                    return;
                }

                try {
                    // Cache file locally
                    String ext = isPdfFormat(docName, docType, url) ? ".pdf" : ".img";
                    File tempFile = File.createTempFile("med_doc_", ext, getCacheDir());
                    tempFile.deleteOnExit();

                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();
                    }

                    cachedFile = tempFile;

                    mainHandler.post(() -> renderCachedFile(tempFile));

                } catch (Exception e) {
                    Log.e(TAG, "Error writing cached document: " + e.getMessage(), e);
                    showError("Unable to load document", "Could not process document content: " + e.getMessage());
                }
            }
        });
    }

    private boolean isPdfFormat(String name, String type, String url) {
        String combined = (name + " " + type + " " + url).toLowerCase(Locale.ROOT);
        return combined.contains(".pdf") || "pdf".equalsIgnoreCase(type);
    }

    private void renderCachedFile(File file) {
        try {
            if (isPdfFormat(docName, docType, rawUrl)) {
                renderPdfFile(file);
            } else {
                renderImageFile(file);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to render file: " + e.getMessage(), e);
            // If PDF rendering failed, try fallback image decoding
            try {
                renderImageFile(file);
            } catch (Exception ex) {
                showError("Unable to load document", "The file format could not be rendered natively inside the app.");
            }
        }
    }

    private void renderPdfFile(File file) throws IOException {
        closePdfRenderer();

        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        pdfRenderer = new PdfRenderer(pfd);
        totalPdfPages = pdfRenderer.getPageCount();

        if (totalPdfPages <= 0) {
            showError("Invalid PDF", "The PDF document contains no renderable pages.");
            return;
        }

        if (totalPdfPages > 1) {
            layoutPdfPageNav.setVisibility(View.VISIBLE);
        } else {
            layoutPdfPageNav.setVisibility(View.GONE);
        }

        renderPdfPage(0);
    }

    private void renderPdfPage(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= totalPdfPages) return;

        showLoading(true, "Rendering page " + (pageIndex + 1) + "...");

        try {
            PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);

            // Render at high resolution (density scale ~2.5x) for maximum crisp readability
            float density = getResources().getDisplayMetrics().density;
            int renderWidth = Math.round(page.getWidth() * Math.max(density, 2.0f));
            int renderHeight = Math.round(page.getHeight() * Math.max(density, 2.0f));

            Bitmap bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();

            currentPdfPage = pageIndex;
            tvPageIndicator.setText(String.format(Locale.US, "Page %d of %d", currentPdfPage + 1, totalPdfPages));
            btnPrevPage.setEnabled(currentPdfPage > 0);
            btnNextPage.setEnabled(currentPdfPage < totalPdfPages - 1);

            zoomableDocView.setImageBitmap(bitmap);
            zoomableDocView.setVisibility(View.VISIBLE);
            zoomableDocView.fitImageToView();
            showLoading(false, "");

        } catch (Exception e) {
            Log.e(TAG, "Error rendering PDF page " + pageIndex + ": " + e.getMessage(), e);
            showError("Render Error", "Failed to render PDF page: " + e.getMessage());
        }
    }

    private void renderImageFile(File file) {
        showLoading(true, "Loading image...");

        try {
            // First decode bounds
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            // Calculate sample size to prevent OOM on very large scans
            int reqWidth = 2048;
            int reqHeight = 2048;
            int inSampleSize = 1;

            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }

            options.inSampleSize = inSampleSize;
            options.inJustDecodeBounds = false;

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap != null) {
                layoutPdfPageNav.setVisibility(View.GONE);
                zoomableDocView.setImageBitmap(bitmap);
                zoomableDocView.setVisibility(View.VISIBLE);
                zoomableDocView.fitImageToView();
                showLoading(false, "");
            } else {
                showError("Unable to load document", "Could not decode medical image file.");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to decode image file: " + e.getMessage(), e);
            showError("Unable to load document", "Error reading image data: " + e.getMessage());
        }
    }

    // =========================================================================
    // Download Action (Requirement 9)
    // =========================================================================

    private void downloadDocumentFile() {
        if (rawUrl == null || rawUrl.isEmpty()) {
            Toast.makeText(this, "Document download URL unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String safeFileName = docName != null && !docName.isEmpty() ? docName : "medical_record";
            if (!safeFileName.contains(".")) {
                safeFileName += isPdfFormat(docName, docType, rawUrl) ? ".pdf" : ".jpg";
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(rawUrl));
            request.setTitle(docName);
            request.setDescription("Downloading medical document...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFileName);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(this, "Downloading " + safeFileName + " to Downloads folder", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "DownloadManager error: " + e.getMessage());
            Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // UI Helpers
    // =========================================================================

    private void showLoading(boolean show, String message) {
        mainHandler.post(() -> {
            if (layoutLoading != null) {
                layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
                if (message != null && tvLoadingMessage != null) {
                    tvLoadingMessage.setText(message);
                }
            }
        });
    }

    private void showError(String title, String details) {
        mainHandler.post(() -> {
            showLoading(false, "");
            layoutError.setVisibility(View.VISIBLE);
            tvErrorDetails.setText(details != null ? details : "Unable to load document.");
            zoomableDocView.setVisibility(View.INVISIBLE);
            layoutPdfPageNav.setVisibility(View.GONE);
        });
    }

    private void closePdfRenderer() {
        try {
            if (pdfRenderer != null) {
                pdfRenderer.close();
                pdfRenderer = null;
            }
            if (pfd != null) {
                pfd.close();
                pfd = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing PdfRenderer: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closePdfRenderer();
        if (cachedFile != null && cachedFile.exists()) {
            try {
                cachedFile.delete();
            } catch (Exception ignored) {}
        }
    }
}
