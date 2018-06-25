
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;
import com.amazonaws.services.rekognition.model.CreateCollectionRequest;
import com.amazonaws.services.rekognition.model.CreateCollectionResult;
import com.amazonaws.services.rekognition.model.DeleteCollectionRequest;
import com.amazonaws.services.rekognition.model.DeleteCollectionResult;
import com.amazonaws.services.rekognition.model.FaceRecord;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.IndexFacesRequest;
import com.amazonaws.services.rekognition.model.IndexFacesResult;
import com.amazonaws.services.rekognition.model.ResourceAlreadyExistsException;
import com.amazonaws.services.rekognition.model.ResourceNotFoundException;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class Project {
	private static String collectionId = "friends";
	private static Scanner scan;

	public static void main(String[] args) throws AmazonRekognitionException, FileNotFoundException, IOException {
		scan = new Scanner(System.in);

		while (true) {
			System.out.println("\nWhat would you like to do?:" + "\nCreate collection --- c"
					+ "\nUpload pictures to collection --- u" + "\nDelete collection --- d\nExit --- e");
			String input = scan.next();

			switch (input.toLowerCase()) {
			case "c":
				try {
					CreateCollection();
				} catch (ResourceAlreadyExistsException e) {
					System.out.println("The resource already exist.");
				}
				break;
			case "u":
				UploadObject();
				break;
			case "d":
				try {
					DeleteCollection();
				} catch (ResourceNotFoundException e) {
					System.out.println("The resource doesn't exist.");
				}
				break;
			case "e":
				System.out.println("Program closed.");
				System.exit(0);
				break;
			default:
				System.out.println("The command doesn't exist.");
				break;
			}
		}
	}

	private static void CreateCollection() throws FileNotFoundException, IOException {
		Properties properties = new Properties();
		properties.load(new FileInputStream(new File("aws_sdk.properties")));

		AWSCredentials credentials = new BasicAWSCredentials(properties.getProperty("aws_access_key_id"),
				properties.getProperty("aws_secret_access_key"));

		AmazonRekognition amazonRekognition = AmazonRekognitionClientBuilder.standard().withRegion(Regions.US_EAST_1)
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

		System.out.println("Creating collection: " + collectionId);

		CreateCollectionRequest request = new CreateCollectionRequest().withCollectionId(collectionId);

		CreateCollectionResult createCollectionResult = amazonRekognition.createCollection(request);
		System.out.println("CollectionArn : " + createCollectionResult.getCollectionArn());
		System.out.println("Status code : " + createCollectionResult.getStatusCode().toString());
	}

	private static void DeleteCollection() throws FileNotFoundException, IOException {
		Properties properties = new Properties();
		properties.load(new FileInputStream(new File("aws_sdk.properties")));

		AWSCredentials credentials = new BasicAWSCredentials(properties.getProperty("aws_access_key_id"),
				properties.getProperty("aws_secret_access_key"));

		AmazonRekognition amazonRekognition = AmazonRekognitionClientBuilder.standard().withRegion(Regions.US_EAST_1)
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

		System.out.println("Deleting collections");

		DeleteCollectionRequest request = new DeleteCollectionRequest().withCollectionId(collectionId);
		DeleteCollectionResult deleteCollectionResult = amazonRekognition.deleteCollection(request);

		System.out.println(collectionId + ": " + deleteCollectionResult.getStatusCode().toString());
	}

	private static void UploadObject() throws FileNotFoundException, IOException {

		Properties properties = new Properties();
		properties.load(new FileInputStream(new File("aws_sdk.properties")));

		AWSCredentials credentials = new BasicAWSCredentials(properties.getProperty("aws_access_key_id"),
				properties.getProperty("aws_secret_access_key"));

		ClientConfiguration clientConfig = new ClientConfiguration();
		clientConfig.setProtocol(Protocol.HTTP);
		AmazonS3 s3 = new AmazonS3Client(credentials, clientConfig);
		AmazonRekognition amazonRekognition = AmazonRekognitionClientBuilder.standard().withRegion(Regions.US_EAST_1)
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

		String bucketCollection = properties.getProperty("bucket");

		File folder = new File("Resources/Pictures");
		if (folder.isDirectory()) {
			for (File picture : folder.listFiles()) {
				try {

					String keyName = picture.getName();
					System.out.println("Uploading a new object to S3 from a file\n");
					s3.putObject(new PutObjectRequest(bucketCollection, keyName, picture));

					Image image = new Image()
							.withS3Object(new S3Object().withBucket(bucketCollection).withName(keyName));

					IndexFacesRequest indexFacesRequest = new IndexFacesRequest().withImage(image)
							.withCollectionId(collectionId).withExternalImageId(keyName).withDetectionAttributes("ALL");

					IndexFacesResult indexFacesResult = amazonRekognition.indexFaces(indexFacesRequest);

					System.out.println(keyName + " added");
					List<FaceRecord> faceRecords = indexFacesResult.getFaceRecords();
					for (FaceRecord faceRecord : faceRecords) {
						System.out.println("Face detected: Faceid is " + faceRecord.getFace().getFaceId());
					}

				} catch (AmazonClientException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
