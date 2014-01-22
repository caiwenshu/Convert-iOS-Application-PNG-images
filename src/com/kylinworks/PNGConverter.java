/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.kylinworks;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.CRC32;

/**
 *
 * @author Rex
 */


public class PNGConverter {
    

    public PNGConverter(File myImgFile) {
    	
   	   new ConvertHandler( myImgFile).start();
    	
    }

    
    public static void main( String args[] ) {
    	if(args.length > 0) {
            File file = new File(args[0]);
            new PNGConverter(file);
        }
    }
    

class ConvertHandler extends Thread {
    File m_file;
    public ConvertHandler( File file ) {
        m_file = file;
    }

    public void run() {
        if( m_file.isDirectory() ) {
            convertDirectory( m_file );
        }
        else if( isPNGFile( m_file ) ) {
            convertPNGFile( m_file );
        }
    }

    protected  ArrayList<PNGTrunk> trunks = null;

    protected boolean isPNGFile( File file ) {
        String szFileName = file.getName();
        if( szFileName.length()<5) {
            return false;
        }
        return szFileName.substring( szFileName.length()-4).equalsIgnoreCase(".png");
    }

    protected  PNGTrunk getTrunk( String szName ) {
        if( trunks==null ) {
            return null;
        }
        PNGTrunk trunk;
        PNGTrunk returnTrunk = null;
        
         for( int n=0; n<trunks.size(); n++ ) {
            trunk = trunks.get( n );
            if( trunk.getName().equalsIgnoreCase( szName)) {
            	if (returnTrunk == null)
            		returnTrunk = trunks.get( n );
            	else
            		returnTrunk.appendTrunk(trunks.get( n ));
            }
        }
        return returnTrunk;
    }

    public void convertPNGFile( File pngFile ) {
        String szFullPath = pngFile.getAbsolutePath();
        String newFileName =szFullPath.substring( 0, szFullPath.lastIndexOf( File.separator)+1 ) + pngFile.getName().substring(0, pngFile.getName().lastIndexOf("."))+"-new.png";

        try{
            DataInputStream file = new DataInputStream( new FileInputStream(pngFile) );
            FileOutputStream output = null;
            byte[] nPNGHeader = new byte[8];
            file.read( nPNGHeader );

            boolean bWithCgBI = false;

            trunks = new ArrayList<PNGTrunk>();
            PNGTrunk trunk;
            if((nPNGHeader[0]==-119)&&(nPNGHeader[1]==0x50)&&(nPNGHeader[2]==0x4e)&&(nPNGHeader[3]==0x47)
                     &&(nPNGHeader[4]==0x0d)&&(nPNGHeader[5]==0x0a)&&(nPNGHeader[6]==0x1a)&&(nPNGHeader[7]==0x0a) ) {

                do {
                    trunk =  PNGTrunk.generateTrunk( file );
                    trunks.add( trunk );

                    if( trunk.getName().equalsIgnoreCase( "CgBI") ) {
                        bWithCgBI = true;
                    }
                }
                while(!trunk.getName().equalsIgnoreCase( "IEND"));
            }
            file.close();

            if( getTrunk( "CgBI" )!=null ) {
                String szInfo = "Dir:"+pngFile.getAbsolutePath() + "--->" +newFileName;
                System.out.println( "Dir:"+pngFile.getAbsolutePath() + "--->" +newFileName );
                
                // Convert data
               PNGTrunk dataTrunk = getTrunk( "IDAT" );
               
               System.out.println("dataTrunk size = "+dataTrunk.getSize());

               PNGIHDRTrunk ihdrTrunk = (PNGIHDRTrunk) getTrunk( "IHDR" );
               System.out.println( "Width:"+ihdrTrunk.m_nWidth+"  Height:"+ihdrTrunk.m_nHeight);

               int nMaxInflateBuffer = 4*(ihdrTrunk.m_nWidth + 1)*ihdrTrunk.m_nHeight;
               byte[] outputBuffer = new byte[nMaxInflateBuffer];

               ZStream inStream = new ZStream();
               inStream.avail_in = dataTrunk.getSize();
               inStream.next_in_index = 0;
               inStream.next_in = dataTrunk.getData();
               inStream.next_out_index = 0;
               inStream.next_out = outputBuffer;
               inStream.avail_out = outputBuffer.length;

               if( inStream.inflateInit( -15  )!=JZlib.Z_OK ) {
                   System.out.println( "PNGCONV_ERR_ZLIB" );
                   return;
               }

                int nResult = inStream.inflate( JZlib.Z_NO_FLUSH);
                switch (nResult) {
                    case JZlib.Z_NEED_DICT:
                        nResult = JZlib.Z_DATA_ERROR;     /* and fall through */
                    case JZlib.Z_DATA_ERROR:
                    case JZlib.Z_MEM_ERROR:
                        inStream.inflateEnd();
                        System.out.println( "PNGCONV_ERR_ZLIB" );
                        return;
                }

                nResult = inStream.inflateEnd();

                if (inStream.total_out > nMaxInflateBuffer) {
                    System.out.println( "PNGCONV_ERR_INFLATED_OVER" );
                }

                // Switch the color
               int nIndex = 0;
               byte nTemp;
               for (int y = 0; y < ihdrTrunk.m_nHeight; y++) {
                    nIndex++;
                    for (int x= 0; x < ihdrTrunk.m_nWidth; x++) {
                        nTemp = outputBuffer[nIndex];
                        outputBuffer[nIndex] = outputBuffer[nIndex+2];
                        outputBuffer[nIndex+2] = nTemp;
                        nIndex += 4;
                    }
                }

                ZStream deStream = new ZStream();
                int nMaxDeflateBuffer = nMaxInflateBuffer+1024;
                byte[] deBuffer = new byte[nMaxDeflateBuffer];

                deStream.avail_in = (int) outputBuffer.length;
                deStream.next_in_index = 0;
                deStream.next_in = outputBuffer;
                deStream.next_out_index = 0;
                deStream.next_out = deBuffer;
                deStream.avail_out = deBuffer.length;
                deStream.deflateInit( 9 );
                nResult = deStream.deflate( JZlib.Z_FINISH);


                if (deStream.total_out > nMaxDeflateBuffer ) {
                    System.out.println( "PNGCONV_ERR_DEFLATED_OVER" );
                }
                byte[] newDeBuffer = new byte[(int)deStream.total_out];
                for( int n=0; n<deStream.total_out; n++ ) {
                    newDeBuffer[n] = deBuffer[n];
                }
                CRC32 crc32 = new CRC32();
                crc32.update( dataTrunk.getName().getBytes() );
                crc32.update(newDeBuffer );
                long lCRCValue = crc32.getValue();

                dataTrunk.m_nData = newDeBuffer;
                dataTrunk.m_nCRC[0] = (byte) ( (lCRCValue&0xFF000000)>>24 );
                dataTrunk.m_nCRC[1] = (byte) ( (lCRCValue&0xFF0000)>>16 );
                dataTrunk.m_nCRC[2] = (byte) ( (lCRCValue&0xFF00)>>8 );
                dataTrunk.m_nCRC[3] = (byte) ( lCRCValue&0xFF );
                dataTrunk.m_nSize = newDeBuffer.length;

                FileOutputStream outStream = new FileOutputStream( newFileName );
                byte[] pngHeader = { -119, 80, 78, 71, 13, 10, 26, 10 };
                outStream.write(pngHeader);
                for( int n=0; n<trunks.size(); n++ ) {
                    trunk = trunks.get( n );
                    if( trunk.getName().equalsIgnoreCase( "CgBI")) {
                        continue;
                    }
                    trunk.writeToStream( outStream );
                }
                outStream.close();
            }
        }catch(IOException e) {
            System.out.println("Error --" + e.toString());
        }

        try {
            sleep( 100 );
        }
        catch( Exception e ) {
            // No nothing now.
        }
    }

    private void convertDirectory( File dir ) {
        File[] files = dir.listFiles();

        for( int n=0; n<files.length; n++ ) {
            if( files[n].isDirectory() ) {
                convertDirectory( files[n] );
            }
            else if( isPNGFile( files[n])) {
                convertPNGFile( files[n] );
            }
        }
    }
}
}
