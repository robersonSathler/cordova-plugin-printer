/*
 Copyright 2013 Sebastián Katzer

 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

package de.appplant.cordova.plugin.printer;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.webkit.WebView;
import android.util.Log;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.DialogInterface;

import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import cielo.printer.client.PrinterAttributes;
import cielo.sdk.order.PrinterListener;
import cielo.sdk.printer.PrinterManager;

/**
 * Plugin to print HTML documents. Therefore it creates an invisible web view
 * that loads the markup data. Once the page has been fully rendered it takes
 * the print adapter of that web view and initializes a print job.
 */
public final class Printer extends CordovaPlugin
{
    /**
     * Executes the request.
     *
     * This method is called from the WebView thread.
     * To do a non-trivial amount of work, use:
     *     cordova.getThreadPool().execute(runnable);
     *
     * To run on the UI thread, use:
     *     cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action   The action to execute.
     * @param args     The exec() arguments in JSON form.
     * @param callback The callback context used when calling back into JavaScript.
     *
     * @return         Whether the action was valid.
     */

    private Builder createDialog(CordovaInterface cordova) {
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            return new Builder(cordova.getActivity(), AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        } else {
            return new Builder(cordova.getActivity());
        }
    }

    private void changeTextDirection(Builder dlg){
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        dlg.create();
        AlertDialog dialog =  dlg.show();
        if (currentapiVersion >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            TextView messageview = (TextView)dialog.findViewById(android.R.id.message);
            messageview.setTextDirection(android.view.View.TEXT_DIRECTION_LOCALE);
        }
    }

    public synchronized void alert(final String message, final String title, final String buttonLabel, final CallbackContext callbackContext) {
    	final CordovaInterface cordova = this.cordova;

        Runnable runnable = new Runnable() {
            public void run() {

                Builder dlg = createDialog(cordova); // new AlertDialog.Builder(cordova.getActivity(), AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                dlg.setMessage(message);
                dlg.setTitle(title);
                dlg.setCancelable(true);
                dlg.setPositiveButton(buttonLabel,
                        new AlertDialog.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, 0));
                            }
                        });
                dlg.setOnCancelListener(new AlertDialog.OnCancelListener() {
                    public void onCancel(DialogInterface dialog)
                    {
                        dialog.dismiss();
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, 0));
                    }
                });

                changeTextDirection(dlg);
            };
        };
        this.cordova.getActivity().runOnUiThread(runnable);
    }


    @Override
    public boolean execute (String action, JSONArray args,
                            CallbackContext callback)
    {
        boolean valid = true;

        if (action.equalsIgnoreCase("check"))
        {
            check(args.optString(0), callback);
        }
        else if (action.equalsIgnoreCase("types"))
        {
            types(callback);
        }
        else if (action.equalsIgnoreCase("print"))
        {

            try {

                print(args.optString(0), args.optJSONObject(1), callback);
                valid = true;

            } catch (Exception e) {

                callback.error(e.getMessage());
                valid = false;
            }

        }
        else {
            valid = false;
        }

        return valid;
    }

    /**
     * If the print framework is able to render the referenced file.
     *
     * @param item     Any kind of URL like file://, file:///, res:// or base64://
     * @param callback The plugin function to invoke with the result.
     */
    private void check (@Nullable String item, CallbackContext callback)
    {
        cordova.getThreadPool().execute(() -> {
            PrintManager pm   = new PrintManager(cordova.getContext());
            boolean printable = pm.canPrintItem(item);

            sendPluginResult(callback, printable);
        });
    }

    /**
     * List of all printable document types (utis).
     *
     * @param callback The plugin function to invoke with the result.
     */
    private void types (CallbackContext callback)
    {
        cordova.getThreadPool().execute(() -> {
            JSONArray utis = PrintManager.getPrintableTypes();

            PluginResult res = new PluginResult(
                    Status.OK, utis);

            callback.sendPluginResult(res);
        });
    }

    /**
     * Sends the provided content to the printing controller and opens
     * them.
     *
     * @param content  The content or file to print.
     * @param settings Additional settings how to render the content.
     * @param callback The plugin function to invoke with the result.
     */
    private void print (@Nullable String content, JSONObject settings, CallbackContext callback) throws Exception
    {

        cordova.getThreadPool().execute(() -> {
            PrinterManager printerManager = new PrinterManager(cordova.getContext());

            PrinterListener printerListener = new PrinterListener() {
                @Override public void onPrintSuccess() {
                    callback.success();
                }

                @Override public void onError(@Nullable Throwable e) {
                    callback.error(e.getMessage());
                    
                }

                @Override public void onWithoutPaper() {
                    callback.error("Sem papel");
                }
            };

            // HashMap<String, Integer> alignLeft =  new HashMap<>();
            // alignLeft.put(PrinterAttributes.KEY_ALIGN, PrinterAttributes.VAL_ALIGN_LEFT);
            // alignLeft.put(PrinterAttributes.KEY_MARGIN_TOP, 5);
            // alignLeft.put(PrinterAttributes.KEY_MARGIN_LEFT, 10);
            // alignLeft.put(PrinterAttributes.KEY_MARGIN_BOTTOM, 50);
            // alignLeft.put(PrinterAttributes.KEY_TYPEFACE, 0);
            // alignLeft.put(PrinterAttributes.KEY_TEXT_SIZE, 20);

            // printerManager.printText(content, alignLeft, printerListener);
            //printerManager.printImage(convertHtmlToImage(content), alignLeft, printerListener);

            try {
                if(content != null) {
                    List<ObjJson> listObj = new ArrayList<ObjJson>();
                    JSONArray jArray = new JSONArray(content);
                    for(int i = 0 ; i < jArray.length() ; i++){
                        ObjJson jObj = new ObjJson();
                        jObj.valor = jArray.getJSONObject(i).getString("valor");
                        jObj.tipo = jArray.getJSONObject(i).getString("tipo");

                        jObj.fonteTipo = Integer.parseInt(jArray.getJSONObject(i).getString("fonteTipo"));
                        jObj.fonteTamanho = Integer.parseInt(jArray.getJSONObject(i).getString("fonteTamanho"));
                        //jObj.fonteFormatacao = jArray.getJSONObject(i).getString("fonteFormatacao");
                        jObj.fonteOrientacao = jArray.getJSONObject(i).getString("fonteOrientacao");

                        //jObj.imagemTamanhoX = Integer.parseInt(jArray.getJSONObject(i).getString("imagemTamanhoX"));
                        //jObj.imagemTamanhoY = Integer.parseInt(jArray.getJSONObject(i).getString("imagemTamanhoY"));
                        
                        jObj.margemEsquesda = Integer.parseInt(jArray.getJSONObject(i).getString("margemEsquesda"));
                        jObj.margemDireita = Integer.parseInt(jArray.getJSONObject(i).getString("margemDireita"));
                        jObj.margemSuperior = Integer.parseInt(jArray.getJSONObject(i).getString("margemSuperior"));
                        jObj.margemInferior = Integer.parseInt(jArray.getJSONObject(i).getString("margemInferior"));

                        jObj.espacamentoEntreLinhas = Integer.parseInt(jArray.getJSONObject(i).getString("espacamentoEntreLinhas"));

                        jObj.pesoColuna = Integer.parseInt(jArray.getJSONObject(i).getString("pesoColuna"));

                        listObj.add(jObj);
                    }

                    HashMap<String, Integer> alignLeft =  new HashMap<>();
                    alignLeft.put(PrinterAttributes.KEY_ALIGN, PrinterAttributes.VAL_ALIGN_LEFT);
                    alignLeft.put(PrinterAttributes.KEY_MARGIN_TOP, 50);
                    alignLeft.put(PrinterAttributes.KEY_MARGIN_BOTTOM, 50);
                    alignLeft.put(PrinterAttributes.KEY_TYPEFACE, 0);
                    alignLeft.put(PrinterAttributes.KEY_TEXT_SIZE, 20);

                    printerManager.printText("Autorizacao: 1234532 29-08-2019 21:43", alignLeft, printerListener);
                    printerManager.printText(" ", alignLeft, printerListener);
                    printerManager.printText("Posto Trevao", alignLeft, printerListener);
                    printerManager.printText("05.342.123/0001-45", alignLeft, printerListener);



                    // https://developercielo.github.io/manual/cielo-lio
                    // for(int i = 0 ; i < listObj.size() ; i++){
                    //     HashMap<String, Integer> set =  new HashMap<>();
                    //     if(listObj.get(i).tipo == "texto") {
                    //         int key_align = listObj.get(i).fonteOrientacao != null || listObj.get(i).fonteOrientacao == "VAL_ALIGN_LEFT"
                    //             ? PrinterAttributes.VAL_ALIGN_LEFT
                    //             : listObj.get(i).fonteOrientacao == "VAL_ALIGN_RIGHT"
                    //             ? PrinterAttributes.VAL_ALIGN_RIGHT
                    //             : PrinterAttributes.VAL_ALIGN_CENTER;
                    //         set.put(PrinterAttributes.KEY_ALIGN, key_align);
                    //         set.put(PrinterAttributes.KEY_TEXT_SIZE, listObj.get(i).fonteTamanho);
                    //         // Trabalha com um inteiro de 0 a 8, onde cada um é uma fonte diferente.
                    //         //set.put(PrinterAttributes.KEY_TYPE_FACE, listObj.get(i).fonteTipo);
                    //         set.put(PrinterAttributes.KEY_MARGIN_LEFT, listObj.get(i).margemEsquesda);
                    //         set.put(PrinterAttributes.KEY_MARGIN_RIGHT, listObj.get(i).margemDireita);
                    //         set.put(PrinterAttributes.KEY_MARGIN_TOP, listObj.get(i).margemSuperior);
                    //         set.put(PrinterAttributes.KEY_MARGIN_BOTTOM, listObj.get(i).margemInferior);
                    //         set.put(PrinterAttributes.KEY_LINE_SPACE, listObj.get(i).espacamentoEntreLinhas);
                    //         // Varíavel utilizada quando se trbaalho com impressão de múltiplas colunas, 
                    //         // para escolher o peso de cada coluna.
                    //         // set.put(PrinterAttributes.KEY_WEIGHT, listObj.get(i).pesoColuna);
                    //     }
                    //     printerManager.printText(listObj.get(i).valor, set, printerListener);
                    // }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            
        });
    }

    public class ObjJson {
        String id = "";
		String valor = "";
		String tipo = "";
		
		// se tipo for fonte usar estes parametros
		int fonteTipo;
		int fonteTamanho;
		//int fonteFormatacao;
		String fonteOrientacao;
		
		// se tipo for imagem usar estes parametros
		// int imagemTamanhoX;
		// int imagemTamanhoY;

		// definicao de margens
        int margemEsquesda;
        int margemDireita;
        int margemSuperior;
        int margemInferior;

        int espacamentoEntreLinhas;

        int pesoColuna;
	}

//     private Bitmap convertHtmlToImage (String html) {

//         html = "<div class='label label-ios'><p>Autorização 1148975</p><p padding-bottom=''>25/08/2019 16:33</p><p>Posto Trevão</p><p>Futuravix</p><p>Motorista: MOTORISTAS DIVERSOS</p><p>Veículo: MRT0033</p><p>Produto: Gasolina Comum</p><p>Qtd: 100</p><p>Valor: 100</p></div>";
// System.out.println(html);
        
        
//         JLabel label = new JLabel(html);
//         label.setSize(200, 120);
        
//         BufferedImage image = new BufferedImage(label.getWidth(), label.getHeight(), BufferedImage.TYPE_INT_ARGB);
        
//         {
//             // paint the html to an image
//             Graphics g = image.getGraphics();
//             g.setColor(Color.BLACK);
//             label.paint(g);
//             g.dispose();
//         }
        
//         // get the byte array of the image (as jpeg)
//         ByteArrayOutputStream baos = new ByteArrayOutputStream();
//         byte[] bytes = null;
//         try {
//             ImageIO.write(image, "bmp", baos);
//             bytes = baos.toByteArray();
//         } catch (Exception ex) {

//         }
        

// System.out.println(bytes);
//         //Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.cielo);
//         Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//         printerManager.printImage(bitmap, alignCenter, printerListener);
//         return bitmap;
//     }

    /**
     * Sends the result back to the client.
     *
     * @param callback The callback to invoke.
     * @param value    The argument to pass with.
     */
    private void sendPluginResult (@NonNull CallbackContext callback,
                                   boolean value)
    {
        PluginResult result = new PluginResult(Status.OK, value);

        callback.sendPluginResult(result);
    }
}
