package com.facepp.demo.util;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

public class PointsMatrix {
	private final String vertexShaderCode =
			// This matrix member variable provides a hook to manipulate
			// the coordinates of the objects that use this vertex shader
			"uniform mat4 uMVPMatrix;" +
					"attribute vec4 vPosition;" + "void main() {" +
					// the matrix must be included as a modifier of gl_Position
					"  gl_Position = vPosition * uMVPMatrix; gl_PointSize = 8.0;" + "}";

	private final String fragmentShaderCode = "precision mediump float;" + "uniform vec4 vColor;" + "void main() {"
			+ "  gl_FragColor = vColor;" + "}";

	private final int mProgram;
	private int mPositionHandle;
	private int mColorHandle;
	private int mMVPMatrixHandle;

	// number of coordinates per vertex in this array

	// Set color with red, green, blue and alpha (opacity) values
	float color[] = { 0.2f, 0.709803922f, 0.898039216f, 1.0f };
	float color_rect[] = { 0X61 / 255.0f, 0XB3 / 255.0f, 0X4D / 255.0f, 1.0f };

	// 画点
	public ArrayList<ArrayList> points = new ArrayList<ArrayList>();



	public PointsMatrix(boolean isFaceCompare) {

		// prepare shaders and OpenGL program
		int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
		int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

		mProgram = GLES20.glCreateProgram(); // create empty OpenGL Program
		GLES20.glAttachShader(mProgram, vertexShader); // add the vertex shader
		// to program
		GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment
		// shader to program
		GLES20.glLinkProgram(mProgram); // create OpenGL program executables
	}

	public void draw(float[] mvpMatrix) {
		// Add program to OpenGL environment
		GLES20.glUseProgram(mProgram);

		// get handle to vertex shader's vPosition member
		mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

		// get handle to fragment shader's vColor member
		mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");

		// get handle to shape's transformation matrix
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
		checkGlError("glGetUniformLocation");

		// Apply the projection and view transformation
		GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
		checkGlError("glUniformMatrix4fv");
		// Enable a handle to the triangle vertices
		GLES20.glEnableVertexAttribArray(mPositionHandle);
		// Set color for drawing the triangle
		GLES20.glUniform4fv(mColorHandle, 1, color_rect, 0);

		GLES20.glUniform4fv(mColorHandle, 1, color, 0);

		//这里在绘制判断，需要调用api判断
		synchronized (this) {
			for (int i = 0; i < points.size(); i++) {
				ArrayList<FloatBuffer> triangleVBList = points.get(i);
				for (int j = 0; j < triangleVBList.size(); j++) {
					FloatBuffer fb = triangleVBList.get(j);
					if (fb != null) {
						GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 0, fb);
						// Draw the point
						GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);
					}
				}
			}
		}

			// Disable vertex array
		GLES20.glDisableVertexAttribArray(mPositionHandle);
	}

	public int loadShader(int type, String shaderCode) {
		// create a vertex shader type (GLES20.GL_VERTEX_SHADER)
		// or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
		int shader = GLES20.glCreateShader(type);

		// add the source code to the shader and compile it
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);

		return shader;
	}

	public static void checkGlError(String glOperation) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e("ceshi", glOperation + ": glError " + error);
			throw new RuntimeException(glOperation + ": glError " + error);
		}
	}

	// 定义一个工具方法，将float[]数组转换为OpenGL ES所需的FloatBuffer
	public FloatBuffer floatBufferUtil(float[] arr) {
		// 初始化ByteBuffer，长度为arr数组的长度*4，因为一个int占4个字节
		ByteBuffer qbb = ByteBuffer.allocateDirect(arr.length * 4);
		// 数组排列用nativeOrder
		qbb.order(ByteOrder.nativeOrder());
		FloatBuffer mBuffer = qbb.asFloatBuffer();
		mBuffer.put(arr);
		mBuffer.position(0);
		return mBuffer;
	}

}
