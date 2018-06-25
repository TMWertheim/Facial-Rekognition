/*
 * 
 * 
 * 
 */

package lambda;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.List;

import javax.imageio.ImageIO;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.FaceMatch;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.services.rekognition.model.SearchFacesByImageRequest;
import com.amazonaws.services.rekognition.model.SearchFacesByImageResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CompareFaces implements RequestHandler<S3Event, String> {

	/*
	 * 
	 * 
	 */

	@Override
	public String handleRequest(S3Event s3event, Context context) {

		context.getLogger().log("This program is running");

		// Create a record to retrieve the bucket and key name
		S3EventNotificationRecord record = s3event.getRecords().get(0);
		String srcBucket = record.getS3().getBucket().getName();
		String srcKey = record.getS3().getObject().getKey();

		// Create S3 Client
		AmazonS3 s3Client = new AmazonS3Client();

		// Add the S3 object to a stream to be deciphered and interpreted
		S3Object s3Object = s3Client.getObject(new GetObjectRequest(srcBucket, srcKey));
		InputStream objectData = s3Object.getObjectContent();

		ByteBuffer imageBytes = null;
		BufferedImage image = null;

		try {
			imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(objectData));
			InputStream imageBytesStream;
			imageBytesStream = new ByteArrayInputStream(imageBytes.array());
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			image = ImageIO.read(imageBytesStream);
			ImageIO.write(image, "jpg", baos);
		} catch (IOException e) {
			context.getLogger().log(e.getMessage());
		}

		// Create a Rekognition client
		AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();

		// Create Detect Labels request and result to test whether the picture
		// has a face in it.
		DetectLabelsRequest detectLabelsRequest = new DetectLabelsRequest().withImage(new Image().withBytes(imageBytes))
				.withMinConfidence((float) 90);
		DetectLabelsResult detectLabelsResult = rekognitionClient.detectLabels(detectLabelsRequest);

		// Creates a list and stores labels into it.
		ObjectMapper objectMapper = new ObjectMapper();

		List<Label> labels = detectLabelsResult.getLabels();
		String labelTest = "";
		for (Label label : labels) {
			try {
				labelTest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(label);
			} catch (JsonProcessingException e) {
				context.getLogger().log(e.getMessage());
			}
			context.getLogger().log(labelTest);
		}

		// Generate a URL code
		java.util.Date expiration = new java.util.Date();
		long milliSeconds = expiration.getTime();

		// Adds an hour
		milliSeconds += 1000 * 60 * 60;
		expiration.setTime(milliSeconds);

		GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(srcBucket, srcKey);
		generatePresignedUrlRequest.setMethod(HttpMethod.GET);
		generatePresignedUrlRequest.setExpiration(expiration);

		URL url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);

		// Create a shortened URL
		try {
			String tinyUrl = "http://tinyurl.com/api-create.php?url=";

			String tinyUrlLookup = tinyUrl + url.toString();
			BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(tinyUrlLookup).openStream()));
			tinyUrl = reader.readLine();
			url = new URL(tinyUrl);
		} catch (IOException e) {
			context.getLogger().log(e.getMessage());
		}

		// Create a SNS Client
		AmazonSNSClient snsClient = new AmazonSNSClient();

		// If the image has the phrase "Human", "Person", or "People" in it,
		// then search the library named "Friends"
		if (labelTest.contains("Human") || labelTest.contains("Person") || labelTest.contains("People")) {

			// Create a Search Faces request and result to search the library.
			SearchFacesByImageRequest searchFacesByImageRequest = new SearchFacesByImageRequest()
					.withCollectionId("friends").withImage(new Image().withBytes(imageBytes))
					.withFaceMatchThreshold(70F).withMaxFaces(2);
			SearchFacesByImageResult searchFacesByImageResult = rekognitionClient
					.searchFacesByImage(searchFacesByImageRequest);

			// Sets variable to see how many matches are made
			int match = 0;

			// Goes through the list and sends a message for every hit made
			List<FaceMatch> faceImageMatches = searchFacesByImageResult.getFaceMatches();
			for (FaceMatch face : faceImageMatches) {
				match++;
				try {
					String a = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(face);

					StringBuilder sb = new StringBuilder(a);
					StringBuilder c = sb.deleteCharAt(0);

					String e = c.toString();
					String b[] = e.split(",");

					String externalImage = b[7].trim();
					externalImage = externalImage.replace("\"", "");
					externalImage = externalImage.replace("externalImageId : ", "");
					externalImage = externalImage.replace(".jpg", "");

					String similarity = b[0].trim();
					similarity = similarity.replace("\"", "");
					String similar[] = similarity.split(" : ");
					similarity = similar[1];
					float value = Float.parseFloat(similarity);
					DecimalFormat format = new DecimalFormat("0.#");
					value = Math.round(value);
					similarity = format.format(value);

					// Alerts that a match was found
					String msg = "\nSomeone with " + similarity + "% similarity to " + externalImage
							+ " was at your door!\n\nSee them here: " + url.toString();

					context.getLogger().log(msg);
					
					PublishRequest publishRequest = new PublishRequest("", msg); //Put SNS ARN here
					snsClient.publish(publishRequest);
				} catch (JsonProcessingException e) {
					context.getLogger().log(e.getMessage());
				}
			}
			// Alerts that no matches were found
			if (match == 0) {
				String msg = "\nAn unidentified person was at your door\n\nSee who here: " + url.toString();
				context.getLogger().log(msg);

				PublishRequest publishRequest = new PublishRequest("", msg); //Put SNS ARN here
				snsClient.publish(publishRequest);
			}
		} else {
			// Alerts that a person wasn't in the picture
			String msg = "\nSomeone rung the doorbell but we were unable to recognize the face\n\nSee who here: "
					+ url.toString();

			context.getLogger().log(msg);
			PublishRequest publishRequest = new PublishRequest("", msg); //Put SNS ARN here
			snsClient.publish(publishRequest);
		}
		context.getLogger().log("End of program");

		return "ok";
	}
}