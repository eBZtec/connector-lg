package br.tec.ebz.connid.connector.lg.utils;

public class Body {

    public Body() {}

    public static String createUserManagerSearchBody(String tipo, String filtro){
        return String.format("""
                        <soapenv:Body>
                                <v2:%1$s>
                                 <v2:filtro>
                                    %2$s
                                   <v11:TiposDeSituacoes>
                                       <arr:int>1</arr:int> <!--1 - Afastamento-->
                        			   <arr:int>2</arr:int> <!--2 - Atividade Normal-->
                        			   <arr:int>3</arr:int> <!--3 - Férias-->
                        			   <arr:int>4</arr:int> <!--4 - Recesso-->
                        			   <arr:int>5</arr:int> <!--5 - Rescisão-->
                                    </v11:TiposDeSituacoes>
                                 </v2:filtro>
                              </v2:%1$s>
                           </soapenv:Body>
                        """,
                tipo,
                filtro
        );
    }

    public static String createModifiedContractsSearchBody(String tipo, String codigoEmpresa,String dataFim, String dataInicio, String operacaoExtra,String complemento) {
        return String.format("""
            <soapenv:Body>
               <v2:%1$s>
                  <v2:filtro>
                     <v1:CodigoDaEmpresa>%2$s</v1:CodigoDaEmpresa>
                     <v1:PeriodoDeBusca>
                        <v1:DataFim>%3$s</v1:DataFim>
                        <v1:DataInicio>%4$s</v1:DataInicio>
                     </v1:PeriodoDeBusca>
                     <v1:TiposDeOperacoes>
                        <v1:TipoDeOperacaoDaOcorrencia>
                           <v1:Valor>1</v1:Valor>
                           <v1:Valor>2</v1:Valor>
                           %5$s
                        </v1:TipoDeOperacaoDaOcorrencia>
                     </v1:TiposDeOperacoes>
                     %6$s
                  </v2:filtro>
               </v2:%1$s>
            </soapenv:Body>
            """,
                tipo,
                codigoEmpresa,
                dataFim,
                dataInicio,
                operacaoExtra,
                complemento
        );
    }
}
