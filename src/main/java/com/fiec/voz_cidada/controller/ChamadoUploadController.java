package com.fiec.voz_cidada.controller;

import com.amazonaws.services.s3.model.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class ChamadoUploadController {

    private final AmazonS3 s3Client;

    /**
     * Constructor for ChamadoUploadController, injecting the AmazonS3 client.
     *
     * @param s3Client The AmazonS3 instance.
     */
    public ChamadoUploadController(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Retrieves an image file from an S3 bucket.
     *
     * @param filename The name of the file to retrieve from the S3 bucket.
     * @return A ResponseEntity containing the image resource and appropriate headers.
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        // Hardcoding bucket name as it was in the original SDK v2 version
        final String bucketName = "vozcidadafiec";


        System.out.println(bucketName);

        try {



            ListObjectsV2Request req = new ListObjectsV2Request()
                    .withBucketName(bucketName);

            ListObjectsV2Result resp = s3Client.listObjectsV2(req);

            for (S3ObjectSummary object : resp.getObjectSummaries()) {
                System.out.println("Nome do objeto: " + object.getKey());
                System.out.println("Tamanho do objeto: " + object.getSize());
            }
        } catch (Exception e){
            System.out.println(e);
        }


        try {
            // GetObjectRequest da v2 é substituído pelo método getObject direto da v1
            S3Object s3Object = s3Client.getObject(bucketName, filename);
            S3ObjectInputStream inputStream = s3Object.getObjectContent();

            // Determina o tipo de mídia baseado na extensão do arquivo
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            String lowerFilename = filename.toLowerCase();
            if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else if (lowerFilename.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            }

            // Define os cabeçalhos HTTP para a resposta
            HttpHeaders headers = new HttpHeaders();
            // content-length é obtido do ObjectMetadata na v1
            headers.setContentLength(s3Object.getObjectMetadata().getContentLength());
            headers.setContentType(mediaType);
            headers.setContentDispositionFormData("attachment", filename);

            // Retorna a imagem como um InputStreamResource
            return new ResponseEntity<>(new InputStreamResource(inputStream), headers, HttpStatus.OK);

        } catch (AmazonServiceException e) {
            // Captura exceções específicas do serviço AWS (S3, etc.)
            System.err.println("S3 service error getting image: " + e.getErrorMessage());
            throw new RuntimeException("Não foi possível recuperar o upload. Erro S3: " + e.getErrorMessage(), e);
        } catch (SdkClientException e) {
            // Captura exceções do lado do cliente (problemas de rede, credenciais, etc.)
            System.err.println("S3 client error getting image: " + e.getMessage());
            throw new RuntimeException("Não foi possível recuperar o upload. Erro do cliente S3: " + e.getMessage(), e);
        } catch (Exception e) {
            // Captura quaisquer outras exceções inesperadas
            System.err.println("Unexpected error getting image: " + e.getMessage());
            throw new RuntimeException("Não foi possível recuperar o upload. Erro inesperado.", e);
        }
    }

    /**
     * Uploads an image file to an S3 bucket.
     *
     * @param image The MultipartFile representing the image to upload.
     * @return The filename of the uploaded image.
     */
    @PostMapping("/file")
    public String saveImage(@RequestParam("image") MultipartFile image) {
        // Hardcoding bucket name as it was in the original SDK v2 version
        final String bucketName = "vozcidadafiec";

        try {
            // Gera um nome de arquivo único usando UUID para evitar colisões
            String originalFilename = image.getOriginalFilename();
            String filename = UUID.randomUUID().toString() + "_" + originalFilename;

            // Cria ObjectMetadata para a versão 1.x
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(image.getSize()); // Define o tamanho do conteúdo
            metadata.setContentType(image.getContentType()); // Define o tipo de conteúdo

            // putObjectRequest da v2 e RequestBody.fromInputStream são substituídos
            // pelo método putObject da v1 que aceita InputStream e ObjectMetadata
            PutObjectResult putObjectResult = s3Client.putObject(bucketName, filename, image.getInputStream(), metadata);

            // Retorna o nome do arquivo após o upload bem-sucedido
            return filename;

        } catch (AmazonServiceException e) {
            // Captura exceções específicas do serviço AWS (S3, etc.)
            System.err.println("S3 service error uploading image: " + e.getErrorMessage());
            throw new RuntimeException("Não foi possível fazer upload da imagem. Erro S3: " + e.getErrorMessage(), e);
        } catch (SdkClientException e) {
            // Captura exceções do lado do cliente (problemas de rede, credenciais, etc.)
            System.err.println("S3 client error uploading image: " + e.getMessage());
            throw new RuntimeException("Não foi possível fazer upload da imagem. Erro do cliente S3: " + e.getMessage(), e);
        } catch (IOException e) {
            // Captura exceções gerais de I/O (ao ler o InputStream do MultipartFile)
            System.err.println("IO error uploading image: " + e.getMessage());
            throw new RuntimeException("Não foi possível fazer upload da imagem. Erro de I/O.", e);
        } catch (Exception e) {
            // Captura quaisquer outras exceções inesperadas
            System.err.println("Unexpected error uploading image: " + e.getMessage());
            throw new RuntimeException("Não foi possível fazer upload da imagem. Erro inesperado.", e);
        }
    }
}
