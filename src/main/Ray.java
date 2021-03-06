package main;

import main.shader.Material;
import main.shapes.Shape;
import main.util.Intersection;
import main.util.Vector3;

import java.util.ArrayList;
import java.util.Random;

public class Ray {

    private Random random;

    private Vector3 startingPoint;
    private Vector3 rayDirection;

    private ArrayList<Shape> shapes;
    private ArrayList<Light> lights;

    private Intersection intersection;

    private int recursionStep;
    private int maxRecursionDepth;

    private Shape ignoreShape;
    private double currentRefractionIndex;
    private double reflectionRate;


    public Ray(Vector3 startingPoint, Vector3 raydirection, ArrayList<Shape> shapes, ArrayList<Light> lights, int recursionStep, int maxRecursionDepth){
        this.startingPoint = startingPoint;
        this.rayDirection = raydirection.normalize();

        this.shapes = shapes;
        this.lights = lights;

        this.recursionStep = recursionStep;
        this.maxRecursionDepth = maxRecursionDepth;
        this.currentRefractionIndex = 1;

        random = new Random();
    }

    /*
    This describes the way of the ray. It will return a color. The color is created incrementally.
     */
    public Vector3 shootRay(){

        Vector3 outputColor = null;

        // Main ray. Looks if it hits something. Will update intersection field if it does.
        shootTraceRay();

        // If something is hit
        if(intersection != null) {

            Material m = getShape().getMaterial();

            Vector3 reflectedColor = null;
            Vector3 localColor;

            if(m.isTransparent()){
                localColor = shootRefractionRay();
            }
            else{
                localColor = m.getAlbedo();
            }

            if(m.isReflective()){
                if(m.isTransparent() && this.reflectionRate > 0){
                    reflectedColor = shootReflectionRay();
                }
                if(!m.isTransparent()){
                    reflectedColor = shootReflectionRay();
                }
            }

            outputColor = m.getLocalColor(this, lights, reflectedColor, localColor);

            if(!(m.isTransparent())){
                outputColor = outputColor.scalarmultiplication(shootShadowRay());
            }

            return outputColor;
        }

        return new Vector3(0.7,0.7,0.7);
    }

    private void shootTraceRay(){
        Intersection intersection = null;
        double distance = Double.MAX_VALUE;

        /* Loops through every object that is in the scene. Only the intersection that is nearest to the screen
        is drawn onto the canvas. If the ray does not hit anything, the intersection field stays null.
         */
        for(Shape s: shapes){

            if(!(s.equals(ignoreShape))){
                Intersection inter = s.intersect(startingPoint, rayDirection);

                // The distance to the nearest intersection
                double d = inter.getNearestIntersection();

            /* check if the new distance is smaller as the last recorded distance. Also checks if the distance is smaller
            than 0*/
                if(d < distance && d >= 0){
                    distance = d;
                    intersection = inter;
                }
            }
        }

        this.intersection = intersection;
    }

    private double shootShadowRay(){

        double shadowRayIntensity = 1;
        int shadowRayCount = 100;

        for(Light light : lights) {

            for(Shape s: shapes){

                if(!(s.getMaterial().isTransparent())){

                    Vector3 shadowRayStartPosition = transposePositionInNormalDirection(true, 0.01);

                    for(int x = 0; x < shadowRayCount; x++) {
                        double xCoord = 0, yCoord = 0, zCoord = 0, d = 0;
                        do {
                            xCoord = random.nextDouble() * 2.0 - 1.0;
                            yCoord = random.nextDouble() * 2.0 - 1.0;
                            zCoord = random.nextDouble() * 2.0 - 1.0;
                            d = xCoord * xCoord + yCoord * yCoord + zCoord * zCoord;
                        } while (d > 1.0 && xCoord == 0 && yCoord == 0 && zCoord == 0);
                        Vector3 offset = new Vector3(xCoord, yCoord, zCoord).normalize();
                        Vector3 shadowRayDirection = light.getPosition().add(offset).normalize();

                        Intersection inter = s.intersect(shadowRayStartPosition, shadowRayDirection);

                        if(inter.hasIntersected()){
                            shadowRayIntensity -= 1./shadowRayCount;
                        }
                    }
                    if(shadowRayIntensity == 0) { //alle Schattenstrahlen haben ein Obj auf dem Web zum Licht getroffen

                    }
                }
            }
        }
        return shadowRayIntensity;
    }

    private Vector3 shootReflectionRay(){

        Vector3 output = null;
        Shape shape = getShape();

        if(recursionStep <= maxRecursionDepth){
            Vector3 transposedIntersectionPoint;
            Vector3 reflectionRayDirection;

            double a = getRayDirection().scalarmultiplication(-1).scalar(getNormal());

            Ray ray;

            // When ray moves outside of object
            if(a < 0 ){

                Vector3 normalReversed = getNormal().scalarmultiplication(-1);
                transposedIntersectionPoint = transposePositionInNormalDirection(false, 0.1);
                reflectionRayDirection = getRayDirection().subtract((normalReversed.scalarmultiplication(2*(getRayDirection().scalar(normalReversed)))));
                ray = new Ray(transposedIntersectionPoint, reflectionRayDirection, shapes, lights ,recursionStep + 1, maxRecursionDepth);
                ray.setCurrentRefractionIndex(currentRefractionIndex);

            }   // When ray moves inside of object
            else{
                transposedIntersectionPoint = transposePositionInNormalDirection(true, 0.1);
                reflectionRayDirection = getRayDirection().subtract((getNormal().scalarmultiplication(2*(getRayDirection().scalar(getNormal())))));
                ray = new Ray(transposedIntersectionPoint, reflectionRayDirection, shapes, lights ,recursionStep + 1, maxRecursionDepth);
            }

            // If the material is not transparent, it will ignore itself in the next ray so that it will not accidently hit itself again.
            if(!shape.getMaterial().isTransparent()){
                ray.setIgnoreShape(getShape());
            }

            return ray.shootRay();

        }

        return output;
    }

    private Vector3 shootRefractionRay(){
        Vector3 output = null;

        if(recursionStep <= maxRecursionDepth){
            // Checks the raydirection of the refraction ray inside or outside of the object
            Vector3 hitNormal = getNormal();

            Vector3 v1 = getRayDirection(); // first ray direction;
            double a = getRayDirection().scalarmultiplication(-1).scalar(hitNormal);

            double i1 = this.currentRefractionIndex;
            double i2;

            boolean inside;

            // When ray moves outside of object
            if(a < 0){
                a *= -1;
                i2 = getNextRefractionIndex();
            }
            else{   // When ray moves inside object

                hitNormal = hitNormal.scalarmultiplication(-1);
                i2 = getShape().getMaterial().getRefractionIndex();
            }

            double i = i1 / i2;
            double b = 1 - (i * i) * (1 - (a * a));

            // Check if angle is too big, if it is reflections occur
            if(b < 0){
                reflectionRate = 1;
                output = this.shootReflectionRay();
            }
            else{
                b = Math.sqrt(b);

                // Calculate reflection rate
                double FVertical = ((i1 * a - i2 * b) / (i1 * a + i2 * b));
                FVertical *= FVertical;
                double FParallel = ((i2 * a - i1 * b) / (i2 * a + i1 * b));
                FParallel *= FParallel;
                reflectionRate = (FVertical + FParallel) / 2.0;

                Vector3 v2;
                if(Math.abs(a - b) < 1e-14){
                    if(i == 1){
                        v2 = v1;
                    }
                    else{
                        v2 = v1.scalarmultiplication(i).add(hitNormal.scalarmultiplication((i*a - a) * -1).normalize());
                    }
                }
                else{
                    v2 = v1.scalarmultiplication(i).add(hitNormal.scalarmultiplication((i*a - b) * -1).normalize());
                }

                // Prepares new refracted ray
                Ray ray = new Ray(getTransposedPositionInRefractedDirection(v2, 0.05), v2, shapes, lights, recursionStep+1, maxRecursionDepth);
                ray.setCurrentRefractionIndex(i2);
                output = ray.shootRay();
            }
        }
        return output;
    }

    /**
     * Transposes the given vector by a fraction of the ray hit normal
     * @param outward A boolean that decides the direction of the normal. If true, the normal pointing outwards is
     *                selected, if false, the normal direction is inward into the hit object.
     * @param percentage The amount of the transposition.
     * @return Returns the position vector that has been transposed
     */
    private Vector3 transposePositionInNormalDirection(boolean outward, double percentage){

        if(outward){
            return getIntersectionPoint().add(getNormal().scalarmultiplication(percentage));
        }
        else{
            return getIntersectionPoint().add(getNormal().scalarmultiplication(percentage).scalarmultiplication(-1));
        }
    }

    private Vector3 getTransposedPositionInRefractedDirection(Vector3 refractedDirection, double percentage){
        return getIntersectionPoint().add(refractedDirection.scalarmultiplication(percentage));
    }

    private double getNextRefractionIndex(){
        Ray ray = new Ray(transposePositionInNormalDirection(true, 0.1), getRayDirection(), shapes, lights, 1,1);
        ray.shootTraceRay();

        // We hit nothing. We assume that it will enter air, thus the refraction index will be one
        if(ray.intersection == null){
            return 1;
        }
        else{
            Vector3 rayHitNormal = ray.getNormal();
            double a = getRayDirection().scalarmultiplication(-1).scalar(rayHitNormal);

            // If the ray leaves an object, we can assume that the ray was inside this medium. We will return its refraction index
            if(a < 0){
                double refractionIndex =  ray.getShape().getMaterial().getRefractionIndex();
                return refractionIndex == 0 ? currentRefractionIndex : refractionIndex;
            }
            else{   // If the ray is about to enter an object, we can assume that we entered air before entering the hit object.
                return 1;
            }
        }
    }

    public Vector3 getStartingPoint(){
        return startingPoint;
    }

    public Vector3 getRayDirection(){
        return rayDirection;
    }

    public boolean hasIntersected(){
        return intersection != null;
    }

    public Intersection getIntersection(){
        return intersection;
    }

    public Vector3 getIntersectionPoint(){
        return rayDirection.scalarmultiplication(intersection.getNearestIntersection()).add(startingPoint);
    }

    public Shape getShape(){
        return intersection.getShape();
    }

    public Vector3 getNormal(){
        return getShape().getNormal(getIntersectionPoint()).normalize();
    }

    private void setIgnoreShape(Shape ignoreShape){
        this.ignoreShape = ignoreShape;
    }

    public double getReflectionRate() {
        return reflectionRate;
    }

    public double getCurrentRefractionIndex() {
        return currentRefractionIndex;
    }

    public void setCurrentRefractionIndex(double currentRefractionIndex) {
        this.currentRefractionIndex = currentRefractionIndex;
    }

}
