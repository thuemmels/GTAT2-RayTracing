package main;

import main.shader.Material;
import main.shapes.Shape;
import main.util.Intersection;
import main.util.Vector3;

import java.util.ArrayList;
import java.util.List;

public class Ray {

    private Vector3 startingPoint;
    private Vector3 rayDirection;
    private List<Shape> shapes;

    private Intersection intersection;
    private boolean hasIntersected;
    private double currentTransmission;
    private List<Shape> intersectedObjects;

    public Ray(Vector3 startingPoint, Vector3 raydirection, List<Shape> shapes){
        this.startingPoint = startingPoint;
        this.rayDirection = raydirection;
        this.shapes = shapes;
        currentTransmission = 1; //default air for now
        intersectedObjects = new ArrayList<>();
    }

    public void shootRay(){ // Preferably a nullable object
        Intersection intersection = null;
        double distance = Double.MAX_VALUE;

                /* Loops through every object that is in the scene. Only the intersection that is nearest to the screen
                is drawn onto the canvas. If the ray does not hit anything, the intersection field stays null.
                 */
        for(Shape s: shapes){
            Intersection inter = s.intersect(startingPoint, rayDirection);

            if(inter.getNearestIntersection() < distance && inter.getNearestIntersection() >= 0){
                distance = inter.getNearestIntersection();
                intersection = inter;
            }
        }

        this.intersection = intersection;
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

    public List<Shape> getShapes(){
        return shapes;
    }

    public double getCurrentTransmission() {
        return currentTransmission;
    }

    public void setCurrentTransmission(double currentTransmission) {
        this.currentTransmission = currentTransmission;
    }

    public void addIntersectedObject(Shape s) {
        intersectedObjects.add(s);
    }

    public List<Shape> getIntersectedObjects() {
        return intersectedObjects;
    }

    public void setIntersectedObjects(List<Shape> s) {
        intersectedObjects = s;
    }

    public void removeIntersectedObject(Shape s) {
        intersectedObjects.remove(s);
    }


    /*public Vector3 getIntersectionPoint(Intersection intersection){
        //
    }*/

    /*public Vector3 getNormal(){

    }*/


}
