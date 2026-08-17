package com.fiapx.videoapi.application.dto;

import java.io.InputStream;

public record VideoDownload(
    InputStream content,
    String filename
) {

}
