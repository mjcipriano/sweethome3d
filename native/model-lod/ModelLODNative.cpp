#include <jni.h>
#include <algorithm>
#include <vector>

#include "meshoptimizer/meshoptimizer.h"

extern "C" JNIEXPORT jobject JNICALL
Java_com_eteks_sweethome3d_j3d_ModelLODGenerator_simplifyNative(
    JNIEnv* env, jclass, jfloatArray positionsArray, jintArray indicesArray,
    jfloat ratio, jfloat error) {
  const jsize floatCount = env->GetArrayLength(positionsArray);
  const size_t vertexCount = static_cast<size_t>(floatCount / 3);
  const jsize sourceIndexCount = env->GetArrayLength(indicesArray);
  if (vertexCount < 3 || sourceIndexCount < 3 || sourceIndexCount % 3 != 0) {
    return NULL;
  }

  std::vector<float> positions(static_cast<size_t>(floatCount));
  env->GetFloatArrayRegion(positionsArray, 0, floatCount, positions.data());

  std::vector<unsigned int> sourceIndices(static_cast<size_t>(sourceIndexCount));
  env->GetIntArrayRegion(indicesArray, 0, sourceIndexCount,
      reinterpret_cast<jint*>(sourceIndices.data()));

  size_t targetIndexCount = static_cast<size_t>(sourceIndexCount * ratio) / 3 * 3;
  targetIndexCount = std::max<size_t>(3, targetIndexCount);
  std::vector<unsigned int> simplified(static_cast<size_t>(sourceIndexCount));
  float resultError = 0;
  size_t indexCount = meshopt_simplify(
      simplified.data(), sourceIndices.data(), sourceIndices.size(),
      positions.data(), vertexCount, sizeof(float) * 3,
      targetIndexCount, error, 0, &resultError);
  if (indexCount >= static_cast<size_t>(sourceIndexCount) || indexCount < 3) {
    return NULL;
  }
  simplified.resize(indexCount);

  std::vector<unsigned int> remap(vertexCount);
  size_t compactVertexCount = meshopt_optimizeVertexFetchRemap(
      remap.data(), simplified.data(), simplified.size(), vertexCount);
  std::vector<int> sourceVertices(compactVertexCount);
  for (size_t oldIndex = 0; oldIndex < vertexCount; oldIndex++) {
    if (remap[oldIndex] < compactVertexCount) {
      sourceVertices[remap[oldIndex]] = static_cast<int>(oldIndex);
    }
  }
  for (size_t i = 0; i < simplified.size(); i++) {
    simplified[i] = remap[simplified[i]];
  }

  jintArray resultIndicesArray = env->NewIntArray(static_cast<jsize>(simplified.size()));
  env->SetIntArrayRegion(resultIndicesArray, 0, static_cast<jsize>(simplified.size()),
      reinterpret_cast<const jint*>(simplified.data()));
  jintArray sourceVerticesArray = env->NewIntArray(static_cast<jsize>(sourceVertices.size()));
  env->SetIntArrayRegion(sourceVerticesArray, 0, static_cast<jsize>(sourceVertices.size()),
      reinterpret_cast<const jint*>(sourceVertices.data()));

  jclass resultClass = env->FindClass(
      "com/eteks/sweethome3d/j3d/ModelLODGenerator$SimplificationResult");
  jmethodID constructor = env->GetMethodID(resultClass, "<init>", "([I[IF)V");
  return env->NewObject(resultClass, constructor, sourceVerticesArray, resultIndicesArray, resultError);
}
